package com.entagen.jenkins

import org.junit.Test

class JenkinsJobManagerTests {
    @Test public void testFindTemplateJobs() {
        JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(templateJobPrefix: "myproj", templateBranchName: "master", jenkinsUrl: "http://dummy.com", gitUrl: "git@dummy.com:company/myproj.git")
        List<String> allJobNames = [
                "myproj-foo-master",
                "otherproj-foo-master",
                "myproj-foo-featurebranch"
        ]
        List<TemplateJob> templateJobs = jenkinsJobManager.findTemplateJobs(allJobNames)
        assert templateJobs.size() == 1
        TemplateJob templateJob = templateJobs.first()
        assert templateJob.jobName == "myproj-foo-master"
        assert templateJob.baseJobName == "myproj-foo"
        assert templateJob.templateBranchName == "master"
    }


    @Test public void testTemplateJobSafeNames() {
        TemplateJob templateJob = new TemplateJob(jobName: "myproj-foo-master", baseJobName: "myproj-foo", templateBranchName: "master")

        assert "myproj-foo-myfeature" == templateJob.jobNameForBranch("myfeature")
        assert "myproj-foo-ted_myfeature" == templateJob.jobNameForBranch("ted/myfeature")
    }
}
