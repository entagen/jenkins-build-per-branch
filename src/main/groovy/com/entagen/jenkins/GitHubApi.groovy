package com.entagen.jenkins

import groovy.xml.StreamingMarkupBuilder

import java.util.regex.Pattern

@Mixin(HttpHelper)
class GitHubApi extends BranchSource {

    String gitUrl
    Pattern branchNameFilter = null
    String user
    String repo

    public List<ConcreteJob> getBranchJobs(List<TemplateJob> templates) {
        List<ConcreteJob> jobs = []
        List<Map> pullRequests = getGitHubPullRequests()
        for(template in templates) {
            for(pr in pullRequests) {
                String branchName = pr.number
                String jobName = template.jobNameForBranch(branchName)
                String config = setPullRequestRefSpec(template.config)
                jobs << new ConcreteJob(branchName: branchName, jobName: jobName, templateJob: template, config: config)
            }
        }
        return jobs
    }

    protected List<Map> getGitHubPullRequests() {
        if(!user || !repo) {
            def repoInfo = (gitUrl - "git@github.com:" - ".git").split("/")
            this.user = repoInfo.first()
            this.repo = repoInfo.last()
            serverUrl = "https://api.github.com/"
        }
        def response = get(path: "repos/$user/$repo/pulls")
        return response.data
    }

    protected String setPullRequestRefSpec(String config) {
        def xml = new XmlSlurper().parseText(config)
        def remoteConf = xml.scm.userRemoteConfigs."hudson.plugins.git.UserRemoteConfig"
        remoteConf.replaceNode { node ->
            "hudson.plugins.git.UserRemoteConfig"() {
                name("origin")
                refspec("+refs/pull/*/head:refs/remotes/origin/*")
                url(gitUrl)
            }
        }

        def outputBuilder = new StreamingMarkupBuilder()
        return outputBuilder.bind{ mkp.yield xml }
    }

}
