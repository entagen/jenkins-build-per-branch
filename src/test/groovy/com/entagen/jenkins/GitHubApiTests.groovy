package com.entagen.jenkins

import groovy.json.JsonSlurper
import groovy.mock.interceptor.MockFor
import groovyx.net.http.RESTClient
import net.sf.json.JSON
import net.sf.json.JSONObject
import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.XMLUnit
import org.junit.Test

class GitHubApiTests extends GroovyTestCase {

    @Test public void testSetRefSpec() {
        // given
        def api = new GitHubApi(gitUrl: "git@github.com:entagen/jenkins-build-per-branch.git")
        def config = '''<project>
  <scm>
    <userRemoteConfigs>
      <hudson.plugins.git.UserRemoteConfig>
        <name>origin</name>
        <refspec>+refs/heads/*:refs/remotes/origin/*</refspec>
        <url>git@github.com:entagen/jenkins-build-per-branch.git</url>
      </hudson.plugins.git.UserRemoteConfig>
    </userRemoteConfigs>
  </scm>
</project>'''

        // when
        def result = api.setPullRequestRefSpec(config)

        // then
        assert result
        def expected = JENKINS_JOB_PR_CONFIG
        XMLUnit.setIgnoreWhitespace(true)
        def xmlDiff = new Diff(result, expected)
        assert xmlDiff.similar()

    }

    @Test public void testSetRefSpec_fromDefault() {
        // given
        def api = new GitHubApi(gitUrl: "git@github.com:entagen/jenkins-build-per-branch.git")
        def config = JENKINS_JOB_STUB_CONFIG

        // when
        def result = api.setPullRequestRefSpec(config)

        // then
        assert result
        XMLUnit.setIgnoreWhitespace(true)
        def xmlDiff = new Diff(result, JENKINS_JOB_PR_CONFIG)
        assert xmlDiff.similar()

    }

    @Test public void testGetGitHubPullRequests() {
        // given
        def api = new GitHubApi(gitUrl: "git@github.com:entagen/jenkins-build-per-branch.git")

        // when
        def results
        withJsonResponse(GH_PR_STUB_RESPONSE) {
            results = api.getGitHubPullRequests()
        }

        // then
        assert results != null
        assert results.size() == 1
        assert results[0].id == 0
    }

    @Test void testGetBranchJobs() {
        // given
        GitHubApi api = new GitHubApi(gitUrl: "git@github.com:entagen/jenkins-build-per-branch.git")
        TemplateJob template = new TemplateJob(jobName: "jobName", baseJobName: "baseJobName", templateBranchName: "templateBranchName", config: JENKINS_JOB_STUB_CONFIG)

        // when
        List<ConcreteJob> results
        withJsonResponse(GH_PR_STUB_RESPONSE) {
            results = api.getBranchJobs([template])
        }

        assert results != null
        assert results?.size() == 1
        def result = results[0]
        result.jobName == "0_A_PR_description_title"
        result.branchName == "0"
        result.templateJob == template
        XMLUnit.setIgnoreWhitespace(true)
        def xmlDiff = new Diff(result.config, JENKINS_JOB_PR_CONFIG)
        assert xmlDiff.similar()
    }

    void withJsonResponse(String toJson, Closure closure) {
        def json = new JsonSlurper().parseText(toJson)
        MockFor mockRESTClient = new MockFor(RESTClient)
        mockRESTClient.demand.get { Map<String, ?> args ->
            return [data: json]
        }

        mockRESTClient.use {
            closure()
        }
    }

    private final static GH_PR_STUB_RESPONSE = '''[
  {
    "url": "https://api.github.com/repos/entagen/jenkins-build-per-branch/pulls/0",
    "id": 0,
    "html_url": "https://github.com/entagen/jenkins-build-per-branch/pull/0",
    "number": 0,
    "state": "open",
    "title": "A PR description title",
    "user": {
      "login": "jamesdh",
    },
    "body": "A PR description body",
    "head": {
      "ref": "samplePRBranchName"
    }
  }
]'''
    private final static JENKINS_JOB_STUB_CONFIG = '''<project>
  <scm>
    <userRemoteConfigs>
      <hudson.plugins.git.UserRemoteConfig>
        <name></name>
        <refspec></refspec>
        <url>git@github.com:entagen/jenkins-build-per-branch.git</url>
      </hudson.plugins.git.UserRemoteConfig>
    </userRemoteConfigs>
  </scm>
</project>'''

    private final static JENKINS_JOB_PR_CONFIG = '''<project>
  <scm>
    <userRemoteConfigs>
      <hudson.plugins.git.UserRemoteConfig>
        <name>origin</name>
        <refspec>+refs/pull/*/head:refs/remotes/origin/*</refspec>
        <url>git@github.com:entagen/jenkins-build-per-branch.git</url>
      </hudson.plugins.git.UserRemoteConfig>
    </userRemoteConfigs>
  </scm>
</project>'''
}
