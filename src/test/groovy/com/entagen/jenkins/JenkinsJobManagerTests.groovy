package com.entagen.jenkins

import groovy.mock.interceptor.MockFor
import org.junit.Test

class JenkinsJobManagerTests extends GroovyTestCase {
    @Test public void testFindTemplateJobs() {
        JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(templateJobPrefix: "myproj", templateBranchName: "master", jenkinsUrl: "http://dummy.com", gitUrl: "git@dummy.com:company/myproj.git")
        List<String> allJobNames = [
                "myproj-foo-master",
                "otherproj-foo-master",
                "myproj-foo-featurebranch"
        ]
        List<TemplateJob> templateJobs = jenkinsJobManager.findRequiredTemplateJobs(allJobNames)
        assert templateJobs.size() == 1
        TemplateJob templateJob = templateJobs.first()
        assert templateJob.jobName == "myproj-foo-master"
        assert templateJob.baseJobName == "myproj-foo"
        assert templateJob.templateBranchName == "master"
    }


    @Test public void testFindTemplateJobs_withSlash() {
        JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(templateJobPrefix: "myproj", templateBranchName: "my/slashed/branch", jenkinsUrl: "http://dummy.com", gitUrl: "git@dummy.com:company/myproj.git")
        List<String> allJobNames = [
                "myproj-foo-my_slashed_branch",
                "otherproj-foo-master",
                "myproj-foo-featurebranch"
        ]
        List<TemplateJob> templateJobs = jenkinsJobManager.findRequiredTemplateJobs(allJobNames)
        assert templateJobs.size() == 1
        TemplateJob templateJob = templateJobs.first()
        assert templateJob.jobName == "myproj-foo-my_slashed_branch"
        assert templateJob.baseJobName == "myproj-foo"
        assert templateJob.templateBranchName == "my/slashed/branch"
    }


    @Test public void testFindTemplateJobs_noMatchingJobsThrowsException() {
        JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(templateJobPrefix: "myproj", templateBranchName: "master", jenkinsUrl: "http://dummy.com", gitUrl: "git@dummy.com:company/myproj.git")
        List<String> allJobNames = [
                "otherproj-foo-master",
                "myproj-foo-featurebranch"
        ]
        String result = shouldFail(AssertionError) {
            jenkinsJobManager.findRequiredTemplateJobs(allJobNames)
        }

        assert result == "Unable to find any jobs matching template regex: ^(myproj-[^-]*)-(master)\$\nYou need at least one job to match the templateJobPrefix and templateBranchName suffix arguments. Expression: (templateJobs?.size() > 0)"
    }

    @Test public void testTemplateJobSafeNames() {
        TemplateJob templateJob = new TemplateJob(jobName: "myproj-foo-master", baseJobName: "myproj-foo", templateBranchName: "master")

        assert "myproj-foo-myfeature" == templateJob.jobNameForBranch("myfeature")
        assert "myproj-foo-ted_myfeature" == templateJob.jobNameForBranch("ted/myfeature")
    }

    @Test public void testTemplateDrivenJobNames() {
        // given
        JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(templateJobPrefix: "myproj", templateBranchName: "master", jenkinsUrl: "http://dummy.com", gitUrl: "git@dummy.com:company/myproj.git")
        TemplateJob templateJob = new TemplateJob(jobName: "myproj-foo-master", baseJobName: "myproj-foo", templateBranchName: "master")
        List<String> allJobNames = [
                "otherproj-foo-master",
                "myproj-foo-master",
                "myproj-foo-featurebranch"
        ]

        // when
        List<String> results = jenkinsJobManager.templateDrivenJobNames([templateJob], allJobNames)

        // then
        assert results?.size() == 1
        assert results[0] == "myproj-foo-featurebranch"
    }

    @Test public void testInitGitApi_noBranchRegex() {
        JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(gitUrl: "git@dummy.com:company/myproj.git", jenkinsUrl: "http://dummy.com")
        assert jenkinsJobManager.gitApi
    }

    @Test public void testInitGitApi_withBranchRegex() {
        JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(gitUrl: "git@dummy.com:company/myproj.git", branchNameRegex: 'feature\\/.+|release\\/.+|master', jenkinsUrl: "http://dummy.com")
        assert jenkinsJobManager.gitApi
    }

    @Test public void testSyncJobs() {
        // given
        JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(templateJobPrefix: "myproj", templateBranchName: "master", jenkinsUrl: "http://dummy.com", gitUrl: "git@dummy.com:company/myproj.git", noDelete: true)

        def concreteJobs = []
        def templateJob = new TemplateJob(jobName: "myproj-foo-master", baseJobName: "myproj-foo", templateBranchName: "master", config: "<scm></scm>")
        concreteJobs << new ConcreteJob(jobName: "myproj-foo-created", branchName: "created", config: "<scm></scm>", templateJob: templateJob)
        concreteJobs << new ConcreteJob(jobName: "myproj-foo-notcreated", branchName: "notcreated", config: "<scm></scm>", templateJob: templateJob)

        def mockJenkinsApiContext = new MockFor(JenkinsApi)
        mockJenkinsApiContext.demand.cloneJobForBranch(1) { ConcreteJob job, List<TemplateJob> templates ->
            assert concreteJobs.contains(job)
        }
        jenkinsJobManager.jenkinsApi = mockJenkinsApiContext.proxyInstance()

        // when
        jenkinsJobManager.syncJobs(concreteJobs, ["myproj-foo-master", "myproj-foo-created"], [templateJob])

        // then
        mockJenkinsApiContext.verify jenkinsJobManager.jenkinsApi
    }
}
