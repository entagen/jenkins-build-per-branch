package com.entagen.jenkins

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
    Boolean pullRequests = false

    JenkinsApi jenkinsApi
    GitApi gitApi
    GitHubApi gitHubApi

    JenkinsJobManager(Map props) {
        for (property in props) {
            println "Setting property key ${property.key} w/ value ${property.value}"
            this."${property.key}" = property.value
        }
        initJenkinsApi()
        initGitApi()
        initGitHubApi()
    }

    void syncWithRepo() {
//        List<String> allBranchNames = gitApi.branchNames
        List<String> allJobNames = jenkinsApi.jobNames

        // ensure that there is at least one job matching the template pattern, collect the set of template jobs
        List<TemplateJob> templateJobs = findRequiredTemplateJobs(allJobNames)

        List<ConcreteJob> expectedBranchJobs = gitApi.getBranchJobs(templateJobs)
        if(pullRequests) {
            expectedBranchJobs.addAll(gitHubApi.getBranchJobs(templateJobs))
        }

        List<String> allBranchNames = expectedBranchJobs.collect { it.branchName }

        // create any missing template jobs and delete any jobs matching the template patterns that no longer have branches
        syncJobs(expectedBranchJobs, allJobNames, templateJobs)

        // create any missing branch views, scoped within a nested view if we were given one
        if (!noViews) {
            syncViews(allBranchNames)
        }
    }

    public void syncJobs(List<ConcreteJob> expectedJobs, List<String> allJobNames, List<TemplateJob> templateJobs) {
        List<String> currentTemplateDrivenJobNames = templateDrivenJobNames(templateJobs, allJobNames)

        createMissingJobs(expectedJobs, currentTemplateDrivenJobNames, templateJobs)
        if (!noDelete) {
            deleteDeprecatedJobs(currentTemplateDrivenJobNames - expectedJobs.jobName)
        }
    }

    public void createMissingJobs(List<ConcreteJob> expectedJobs, List<String> currentJobs, List<TemplateJob> templateJobs) {
        List<ConcreteJob> missingJobs = expectedJobs.findAll { !currentJobs.contains(it.jobName) }
        if (!missingJobs) return

        for(ConcreteJob missingJob in missingJobs) {
            println "Creating missing job: ${missingJob.jobName} from ${missingJob.templateJob.jobName}"
            jenkinsApi.cloneJobForBranch(missingJob, templateJobs)
            if (startOnCreate) {
                jenkinsApi.startJob(missingJob)
            }
        }
    }

    public void deleteDeprecatedJobs(List<String> deprecatedJobNames) {
        if (!deprecatedJobNames) return
        println "Deleting deprecated jobs:\n\t${deprecatedJobNames.join('\n\t')}"
        deprecatedJobNames.each { String jobName ->
            jenkinsApi.deleteJob(jobName)
        }
    }

    /**
     * Returns a List of job names that were created from templates
     * @param templateJobs
     * @param allJobNames
     * @return
     */
    public List<String> templateDrivenJobNames(List<TemplateJob> templateJobs, List<String> allJobNames) {
        List<String> templateJobNames = templateJobs.jobName
        List<String> templateBaseJobNames = templateJobs.baseJobName

        // don't want actual template jobs, just the jobs that were created from the templates
        return (allJobNames - templateJobNames).findAll { String jobName ->
            templateBaseJobNames.find { String baseJobName -> jobName.startsWith(baseJobName)}
        }
    }

    List<TemplateJob> findRequiredTemplateJobs(List<String> allJobNames) {
        def templateBranchJobName = templateBranchName.replaceAll('/', '_')
        String regex = /^($templateJobPrefix-[^-]*)-($templateBranchJobName)$/
        println "All job names: " + allJobNames.join(",")
        List<TemplateJob> templateJobs = allJobNames.findResults { String jobName ->
            TemplateJob templateJob = null
            jobName.find(regex) { full, baseJobName, branchName ->
                println "Found template job $full"
                String config = jenkinsApi.getJobConfig(full)
                templateJob = new TemplateJob(jobName: full, baseJobName: baseJobName, templateBranchName: templateBranchName, config: config)
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

    GitHubApi initGitHubApi() {
        if (!gitHubApi) {
            assert gitUrl != null
            this.gitHubApi = new GitHubApi(gitUrl: gitUrl)
            if (this.branchNameRegex) {
                this.gitHubApi.branchNameFilter = ~this.branchNameRegex
            }
        }
        return this.gitHubApi
    }
}
