package com.entagen.jenkins

import java.util.regex.Pattern
import groovy.json.JsonSlurper

Boolean failed = false

class JenkinsJobManager {

    String templateJobPrefix
    String templateBranchName
    String gitUrl
    String nestedView
    String jenkinsUrl
    String branchNameRegex
    String jenkinsUser
    String jenkinsPassword

    Boolean dryRun = false
    Boolean noViews = false
    Boolean noDelete = false
    Boolean startOnCreate = false

    JenkinsApi jenkinsApi
    GitApi gitApi

    JenkinsJobManager(Map props) {
        for (property in props) {
            this."${property.key}" = property.value
        }
        initJenkinsApi()
        initGitApi()
    }

    Boolean syncWithRepo() {
        failed = false

        List<String> allBranchNames = gitApi.branchNames.unique{ it.toLowerCase() }
        List<String> allJobNames = jenkinsApi.jobNames

        // ensure that there is at least one job matching the template pattern, collect the set of template jobs
        List<TemplateJob> templateJobs = findRequiredTemplateJobs(allJobNames)

        // create any missing template jobs and delete any jobs matching the template patterns that no longer have branches
        syncJobs(allBranchNames, allJobNames, templateJobs)

        // create any missing branch views, scoped within a nested view if we were given one
        if (!noViews) {
            syncViews(allBranchNames)
        }

        return failed
    }

    public void syncJobs(List<String> allBranchNames, List<String> allJobNames, List<TemplateJob> templateJobs) {
        List<String> currentTemplateDrivenJobNames = templateDrivenJobNames(templateJobs, allJobNames)
        List<String> nonTemplateBranchNames = allBranchNames - templateBranchName
        List<ConcreteJob> expectedJobs = this.expectedJobs(templateJobs, nonTemplateBranchNames)

        createMissingJobs(expectedJobs, currentTemplateDrivenJobNames, templateJobs)
        if (!noDelete) {
            deleteDeprecatedJobs(currentTemplateDrivenJobNames - expectedJobs.jobName)
        }
    }

    public void createMissingJobs(List<ConcreteJob> expectedJobs, List<String> currentJobs, List<TemplateJob> templateJobs) {
        List<String> lowercaseCurrentJobs = currentJobs.collect()*.toLowerCase()
        List<ConcreteJob> missingJobs = expectedJobs.findAll { !lowercaseCurrentJobs.contains(it.jobName.toLowerCase()) }
        if (!missingJobs) return

        for(ConcreteJob missingJob in missingJobs) {
            println "Creating missing job: ${missingJob.jobName} from ${missingJob.templateJob.jobName}"
            try {
                // This creates missing jobs using the missingJob name. Note that templateJobs is a List.
                jenkinsApi.cloneJobForBranch(missingJob, templateJobs)

                // Rev.com-branch is -DtemplateJobPrefix
                // Rev.com-branch-deploy- is the Jenkins job template
                // Rev.com-branch-build- is the Jenkins job template

                if (missingJob.jobName.contains("Rev.com-build-unittest")) {
                    // If the job contains unittest enable it. Jobs start out disabled so -deploy- will remain disabled.
                    jenkinsApi.enableJob(missingJob.jobName)
                }
                if (!missingJob.jobName.contains("Rev.com-deploy-unittest")) {
                    // Prevent double enabling of unittest jobs.
                    if (!missingJob.jobName.contains("Rev.com-build-unittest")) {
                        // If the job doesn't contain unittest enable it. Jobs start out disabled.
                        jenkinsApi.enableJob(missingJob.jobName)
                    }
                }
                if (startOnCreate && missingJob.jobName.contains("Rev.com-build-")) {
                    // Starting -build- has the downstream job of -deploy- when successful.
                    jenkinsApi.startJob(missingJob)
                }
            }
            catch(Exception ex) {
                println(ex.getMessage());
                println(ex.getStackTrace());
                failed = true
            }
        }
    }

    public void deleteDeprecatedJobs(List<String> deprecatedJobNames) {
        if (!deprecatedJobNames) return
        println "Deleting deprecated jobs:\n\t${deprecatedJobNames.join('\n\t')}"
        deprecatedJobNames.each { String jobName ->
            try {
                jenkinsApi.wipeOutWorkspace(jobName)
                jenkinsApi.deleteJob(jobName)
            }
            catch(Exception ex) {
                println(ex.getMessage());
                println(ex.getStackTrace());
                failed = true
            }
        }
    }

    public List<ConcreteJob> expectedJobs(List<TemplateJob> templateJobs, List<String> branchNames) {
        branchNames.collect { String branchName ->
            templateJobs.collect { TemplateJob templateJob -> templateJob.concreteJobForBranch(branchName) }
        }.flatten()
    }

    public List<String> templateDrivenJobNames(List<TemplateJob> templateJobs, List<String> allJobNames) {
        List<String> templateJobNames = templateJobs.jobName
        List<String> templateBaseJobNames = templateJobs.baseJobName

        // don't want actual template jobs, just the jobs that were created from the templates
        return (allJobNames - templateJobNames).findAll { String jobName ->
            templateBaseJobNames.find { String baseJobName -> jobName.startsWith(baseJobName)}
        }
    }

    List<TemplateJob> findRequiredTemplateJobs(List<String> allJobNames) {
        String regex = /^($templateJobPrefix-[^-]*)-($templateBranchName)$/

        List<TemplateJob> templateJobs = allJobNames.findResults { String jobName ->
            TemplateJob templateJob = null
            jobName.find(regex) { full, baseJobName, branchName ->
                templateJob = new TemplateJob(jobName: full, baseJobName: baseJobName, templateBranchName: branchName)
            }
            return templateJob
        }

        assert templateJobs?.size() > 0, "Unable to find any jobs matching template regex: $regex\nYou need at least one job to match the templateJobPrefix and templateBranchName suffix arguments"
        return templateJobs
    }

    public void syncViews(List<String> allBranchNames) {
        List<String> existingViewNames = jenkinsApi.getViewNames(this.nestedView)
        List<BranchView> expectedBranchViews = allBranchNames.collect { String branchName -> new BranchView(branchName: branchName, templateJobPrefix: this.templateJobPrefix) }

        List<BranchView> missingBranchViews = expectedBranchViews.findAll { BranchView branchView -> !existingViewNames.contains(branchView.viewName)}
        addMissingViews(missingBranchViews)

        if (!noDelete) {
            List<String> deprecatedViewNames = getDeprecatedViewNames(existingViewNames, expectedBranchViews)
            deleteDeprecatedViews(deprecatedViewNames)
        }
    }

    public void addMissingViews(List<BranchView> missingViews) {
        println "Missing views: $missingViews"
        for (BranchView missingView in missingViews) {
            jenkinsApi.createViewForBranch(missingView, this.nestedView)
        }
    }

    public List<String> getDeprecatedViewNames(List<String> existingViewNames, List<BranchView> expectedBranchViews) {
         return existingViewNames?.findAll { it.startsWith(this.templateJobPrefix) } - expectedBranchViews?.viewName ?: []
    }

    public void deleteDeprecatedViews(List<String> deprecatedViewNames) {
        println "Deprecated views: $deprecatedViewNames"

        for(String deprecatedViewName in deprecatedViewNames) {
            jenkinsApi.deleteView(deprecatedViewName, this.nestedView)
        }

    }

    JenkinsApi initJenkinsApi() {
        if (!jenkinsApi) {
            assert jenkinsUrl != null
            if (dryRun) {
                println "DRY RUN! Not executing any POST commands to Jenkins, only GET commands"
                this.jenkinsApi = new JenkinsApiReadOnly(jenkinsServerUrl: jenkinsUrl)
            } else {
                this.jenkinsApi = new JenkinsApi(jenkinsServerUrl: jenkinsUrl)
            }

            if (jenkinsUser || jenkinsPassword) this.jenkinsApi.addBasicAuth(jenkinsUser, jenkinsPassword)
        }

        return this.jenkinsApi
    }

    GitApi initGitApi() {
        if (!gitApi) {
            assert gitUrl != null
            this.gitApi = new GitApi(gitUrl: gitUrl)
            if (this.branchNameRegex){
                this.gitApi.branchNameFilter = ~this.branchNameRegex
            }
        }

        return this.gitApi
    }
}
