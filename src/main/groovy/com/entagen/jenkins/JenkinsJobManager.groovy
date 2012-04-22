package com.entagen.jenkins

class JenkinsJobManager {
    String templateJobPrefix
    String templateBranchName
    String gitUrl
    String baseName
    String nestedView
    String jenkinsUrl
    Boolean dryRun = false

    JenkinsApi jenkinsApi
    GitApi gitApi

    JenkinsJobManager(Map props) {
        for (property in props) {
            this."${property.key}" = property.value
        }
        initJenkinsApi()
        initGitApi()
    }

    void syncWithRepo() {
        // get all current branch names from git (might have a '/' in them)
        List<String> allBranchNames = gitApi.branchNames

        // get all jobs from jenkins
        List<String> allJobNames = jenkinsApi.jobNames

        // ensure that there is at least one job matching the template pattern, collect the set of template jobs
        List<TemplateJob> templateJobs = findTemplateJobs(allJobNames)

        // iterate through each git branch, for each, ensure that it has all of the template jobs it should have, create the missing ones


        //

        // collect the jobs matching the pattern that don't have a branch anymore, delete those


        
        List views = jenkinsApi.getViews(nestedView)
        Set viewNames = views.collect {it.name}

        if (true) return

        for (String branchName in branches.keySet()) {
            String branchDisplayName = branches[branchName]
            for (String baseTemplateName in baseTemplateProjectNames.sort()) {
                if (!allJobNames.contains("$baseTemplateName-${branchDisplayName}".toString())) {
                    jenkinsApi.cloneJobForBranch(baseTemplateName, branchName, branchDisplayName, templateBranchName, templateProjectNames)
                }
            }

            if (!viewNames.contains("$baseName-$branchDisplayName".toString())) {
                jenkinsApi.createViewForBranch(baseName, branchDisplayName, templateJobPrefix, nestedView)
            }
        }

        Set expectedBuilds = branches.values().collect {branchDisplayName -> baseTemplateProjectNames.collect {it + "-${branchDisplayName}"} }.flatten()
        Set buildsToDelete = allJobNames - expectedBuilds - templateProjectNames
        println 'toDelete: ' + buildsToDelete

        buildsToDelete.each {
            jenkinsApi.deleteJob(it)
        }

        // Delete views that don't have jobs
        views.each { view ->
            boolean matchesBaseName = view.name.startsWith(baseName + "-")
            if (!view.jobs && matchesBaseName) {
                jenkinsApi.deleteView(view.name, nestedView)
            }
        }
    }

    List<TemplateJob> findTemplateJobs(List<String> allJobNames) {
        allJobNames.findResults { String jobName ->
            TemplateJob templateJob = null
            String regex = /^($templateJobPrefix-.*)-($templateBranchName)$/
            jobName.find(regex) { full, baseJobName, branchName ->
                templateJob = new TemplateJob(jobName: full, baseJobName: baseJobName, templateBranchName: branchName)
            }
            return templateJob
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
        }

        return this.jenkinsApi
    }

    GitApi initGitApi() {
        if (!gitApi) {
            assert gitUrl != null
            this.gitApi = new GitApi(gitUrl: gitUrl)
        }

        return this.gitApi
    }
}
