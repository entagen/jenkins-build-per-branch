package com.entagen.jenkins

class TemplateJob {
    String jobName
    String baseJobName
    String templateBranchName

    String jobNameForBranch(String branchName) {
        // git branches often have a forward slash in them, but they make jenkins cranky, turn it into an underscore
        String safeBranchName = branchName.replaceAll('/', '_')
        return "$baseJobName-$safeBranchName"
    }
    
    ConcreteJob concreteJobForBranch(String branchName) {
        ConcreteJob concreteJob = new ConcreteJob(templateJob: this, branchName: branchName, jobName: jobNameForBranch(branchName) )
    }
}
