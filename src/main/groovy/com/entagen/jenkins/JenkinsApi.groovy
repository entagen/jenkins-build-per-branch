package com.entagen.jenkins

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.RESTClient
import static groovyx.net.http.ContentType.*
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.client.HttpResponseException
import org.apache.http.HttpStatus
import org.apache.http.HttpRequestInterceptor
import org.apache.http.protocol.HttpContext
import org.apache.http.HttpRequest

@Mixin(HttpHelper)
class JenkinsApi {

    public void setJenkinsServerUrl(String jenkinsServerUrl) {
        if (!jenkinsServerUrl.endsWith("/")) jenkinsServerUrl += "/"
        serverUrl = jenkinsServerUrl
        restClient = new RESTClient(jenkinsServerUrl)
    }

    List<String> getJobNames(String prefix = null) {
        println "getting project names from " + serverUrl + "api/json"
        def response = get(path: 'api/json')
        def jobNames = response.data.jobs.name
        if (prefix) return jobNames.findAll { it.startsWith(prefix) }
        return jobNames
    }

    String getJobConfig(String jobName) {
        def response = get(path: "job/${jobName}/config.xml", contentType: TEXT,
                headers: [Accept: 'application/xml'])
        response.data.text
    }

    void cloneJobForBranch(ConcreteJob missingJob, List<TemplateJob> templateJobs) {
        String missingJobConfig = configForMissingJob(missingJob, templateJobs)
        TemplateJob templateJob = missingJob.templateJob

        //Copy job with jenkins copy job api, this will make sure jenkins plugins get the call to make a copy if needed (promoted builds plugin needs this)
        post('createItem', missingJobConfig, [name: missingJob.jobName, mode: 'copy', from: templateJob.jobName], ContentType.XML)

        post('job/' + missingJob.jobName + "/config.xml", missingJobConfig, [:], ContentType.XML)
    }

    void startJob(ConcreteJob job) {
        println "Starting job ${job.jobName}."
        post('job/' + job.jobName + '/build')
    }

    String configForMissingJob(ConcreteJob missingJob, List<TemplateJob> templateJobs) {
        TemplateJob templateJob = missingJob.templateJob
        String config = missingJob.config

        def ignoreTags = ["assignedNode"]

        // should work if there's a remote ("origin/master") or no remote (just "master")
        config = config.replaceAll("(\\p{Alnum}*[>/])(${templateJob.templateBranchName})<") { fullMatch, prefix, branchName ->
            // jenkins job configs may have certain fields whose values should not be replaced, the most common being <assignedNode>
            // which is used to assign a job to a specific node (potentially "master") and the "master" branch
            if (ignoreTags.find { it + ">" == prefix}) {
                return fullMatch
            } else {
                return "$prefix${missingJob.branchName}<"
            }
        }

        // this is in case there are other down-stream jobs that this job calls, we want to be sure we're replacing their names as well
        templateJobs.each {
            config = config.replaceAll(it.jobName, it.jobNameForBranch(missingJob.branchName))
        }

        return config
    }

    void deleteJob(String jobName) {
        println "deleting job $jobName"
        post("job/${jobName}/doDelete")
    }

    void createViewForBranch(BranchView branchView, String nestedWithinView = null) {
        String viewName = branchView.viewName
        Map body = [name: viewName, mode: 'hudson.model.ListView', Submit: 'OK', json: '{"name": "' + viewName + '", "mode": "hudson.model.ListView"}']
        println "creating view - viewName:${viewName}, nestedView:${nestedWithinView}"
        post(buildViewPath("createView", nestedWithinView), body)

        body = [useincluderegex: 'on', includeRegex: "${branchView.templateJobPrefix}.*${branchView.safeBranchName}", name: viewName, json: '{"name": "' + viewName + '","useincluderegex": {"includeRegex": "' + branchView.templateJobPrefix + '.*' + branchView.safeBranchName + '"},' + VIEW_COLUMNS_JSON + '}']
        println "configuring view ${viewName}"
        post(buildViewPath("configSubmit", nestedWithinView, viewName), body)
    }

    List<String> getViewNames(String nestedWithinView = null) {
        String path = buildViewPath("api/json", nestedWithinView)
        println "getting views - nestedWithinView:${nestedWithinView} at path: $path"
        def response = get(path: path, query: [tree: 'views[name,jobs[name]]'])
        response.data?.views?.name
    }

    void deleteView(String viewName, String nestedWithinView = null) {
        println "deleting view - viewName:${viewName}, nestedView:${nestedWithinView}"
        post(buildViewPath("doDelete", nestedWithinView, viewName))
    }

    protected String buildViewPath(String pathSuffix, String... nestedViews) {
        List elems = nestedViews.findAll { it != null }
        String viewPrefix = elems.collect { "view/${it}" }.join('/')

        if (viewPrefix) return "$viewPrefix/$pathSuffix"

        return pathSuffix
    }

    static final String VIEW_COLUMNS_JSON = '''
"columns":[
      {
         "stapler-class":"hudson.views.StatusColumn",
         "kind":"hudson.views.StatusColumn$DescriptorImpl"
      },
      {
         "stapler-class":"hudson.views.WeatherColumn",
         "kind":"hudson.views.WeatherColumn$DescriptorImpl"
      },
      {
         "stapler-class":"hudson.views.JobColumn",
         "kind":"hudson.views.JobColumn$DescriptorImpl"
      },
      {
         "stapler-class":"hudson.views.LastSuccessColumn",
         "kind":"hudson.views.LastSuccessColumn$DescriptorImpl"
      },
      {
         "stapler-class":"hudson.views.LastFailureColumn",
         "kind":"hudson.views.LastFailureColumn$DescriptorImpl"
      },
      {
         "stapler-class":"hudson.views.LastDurationColumn",
         "kind":"hudson.views.LastDurationColumn$DescriptorImpl"
      },
      {
         "stapler-class":"hudson.views.BuildButtonColumn",
         "kind":"hudson.views.BuildButtonColumn$DescriptorImpl"
      }
   ]
'''

}
