package com.entagen.jenkins

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.RESTClient
import static groovyx.net.http.ContentType.*
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.client.HttpResponseException
import org.apache.http.HttpStatus

class JenkinsApi {
    String jenkinsServerUrl
    RESTClient restClient

    public void setJenkinsServerUrl(String jenkinsServerUrl) {
        if (!jenkinsServerUrl.endsWith("/")) jenkinsServerUrl += "/"
        this.jenkinsServerUrl = jenkinsServerUrl
        this.restClient = new RESTClient(jenkinsServerUrl)
    }

    List<String> getJobNames(String prefix = null) {
        println "getting project names from " + jenkinsServerUrl + "api/json"
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
        post('createItem', missingJobConfig, [name: missingJob.jobName], ContentType.XML)
    }

    String configForMissingJob(ConcreteJob missingJob, List<TemplateJob> templateJobs) {
        TemplateJob templateJob = missingJob.templateJob
        String config = getJobConfig(templateJob.jobName)
        // should work if there's a remote ("origin/master") or no remote (just "master")
        config = config.replaceAll("([>/])(${templateJob.templateBranchName})<") { fullMatch, prefix, branchName ->
            return "$prefix${missingJob.branchName}<"
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

    protected get(Map map) {
        // get is destructive to the map, if there's an error we want the values around still
        Map mapCopy = map.clone() as Map
        def response

        assert mapCopy.path != null, "'path' is a required attribute for the GET method"

        try {
            response = restClient.get(map)
        } catch(HttpHostConnectException ex) {
            println "Unable to connect to host: $jenkinsServerUrl"
            throw ex
        } catch(UnknownHostException ex) {
            println "Unknown host: $jenkinsServerUrl"
            throw ex
        } catch(HttpResponseException ex) {
            def message = "Unexpected failure with path $jenkinsServerUrl${mapCopy.path}, HTTP Status Code: ${ex.response?.status}, full map: $mapCopy"
            throw new Exception(message, ex)
        }

        assert response.status < 400
        return response
    }


    /**
     * @author Kelly Robinson
     * from https://github.com/kellyrob99/Jenkins-api-tour/blob/master/src/main/groovy/org/kar/hudson/api/PostRequestSupport.groovy
     */
    protected Integer post(String path, postBody = [:], params = [:], ContentType contentType = ContentType.URLENC) {
        HTTPBuilder http = new HTTPBuilder(jenkinsServerUrl)
        Integer status = HttpStatus.SC_EXPECTATION_FAILED

        http.handler.failure = { resp ->
            println "Unexpected failure on $jenkinsServerUrl$path: ${resp.statusLine} ${resp.status}"
            status = resp.statusLine.statusCode
        }

        http.post(path: path, body: postBody, query: params,
                requestContentType: contentType) { resp ->
            assert resp.statusLine.statusCode < 400
            status = resp.statusLine.statusCode
        }
        return status
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
