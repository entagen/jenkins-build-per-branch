package com.entagen.jenkins

class TemplateJob {
    String jobName
    String baseJobName
    String templateBranchName

    String jobNameForBranch(String gitBranchName) {
        // git branches often have a forward slash in them, but they make jenkins cranky, turn it into an underscore
        String safeBranchName = gitBranchName.replaceAll('/', '_')
        return "$baseJobName-$safeBranchName"
    }
}
