import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.ContentType.TEXT

@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.5.2')

//TODO - parameter-ize these
String templateJobPrefix = 'BuildTripleMap-'
String templateJobSuffix = 'master'
String gitUrl = 'git@github.com:entagen/triplemap.git'

JenkinsApi api = new JenkinsApi('http://macallan:8081/')

//delete all builds that are NOT master branch builds
//Set currentBuilds = api.getProjectNames(templateJobPrefix)
//Set templateProjectNames = currentBuilds.findAll { it.startsWith(templateJobPrefix) }
//templateProjectNames.sort().each {
//    if (!it.endsWith('-master')){
//        api.deleteJob(it)
//    }
//}

Set currentBuilds = api.getProjectNames(templateJobPrefix)

Set templateProjectNames = currentBuilds.findAll { it.startsWith(templateJobPrefix) && it.endsWith('-' + templateJobSuffix) }
Set baseTemplateProjectNames = templateProjectNames.collect {(it.minus('-' + templateJobSuffix))}

String branchesCommand = "git ls-remote --heads ${gitUrl}"

Set branches = []
def process = branchesCommand.execute()
process.waitFor()
process.in.text.eachLine {String line ->
    String branchName = line.substring(line.indexOf('refs/heads/')) - 'refs/heads/'
    if (branchName != templateJobSuffix) {
        branches << branchName
    }
}

for (String branchName in branches) {
    for (String baseTemplateName in baseTemplateProjectNames.sort()) {
        if (!currentBuilds.contains("$baseTemplateName-$branchName".toString())) {
            api.copyJobAndSetBranch(baseTemplateName, branchName, templateJobSuffix, templateProjectNames)
        }
    }
    // there isn't a good way to retrieve a list of views, so just try to create them every time
    // jenkins will just 400 error on the duplicates
    api.createViewForBranch('TripleMap', branchName)

}

Set expectedBuilds = branches.collect {branch -> baseTemplateProjectNames.collect {it + "-${branch}"} }.flatten()
Set buildsToDelete = currentBuilds - expectedBuilds - templateProjectNames
println 'toDelete: ' + buildsToDelete

buildsToDelete.each {
    api.deleteJob(it)
}

// Delete views that don't have jobs
api.getViews().each { view ->
    if (!view.jobs) {
        api.deleteView(view.name)
    }
}

class JenkinsApi {
    String jenkinsServerUrl
    def restClient

    JenkinsApi(jenkinsServerUrl) {
        this.jenkinsServerUrl = jenkinsServerUrl
        this.restClient = new groovyx.net.http.RESTClient(jenkinsServerUrl)
    }

    List<String> getProjectNames(String prefix) {
        println "getting project names"
        def response = restClient.get(path: 'api/json')
        response.data.jobs.name.findAll({it.startsWith(prefix)})
    }

    String getJobConfig(String jobName) {
        def response = restClient.get(path: "job/${jobName}/config.xml", contentType: TEXT,
                headers: [Accept: 'application/xml'])
        response.data.text
    }

    void copyJobAndSetBranch(String baseJobName, String branchName, String templateBranch, Set templateJobNames) {
        String config = getJobConfig("$baseJobName-$templateBranch")
        config = config.replaceAll(">origin/${templateBranch}<", ">origin/${branchName}<")

        templateJobNames.each {String templateJobName ->
            String baseName = templateJobName - templateBranch
            config = config.replaceAll(templateJobName, "${baseName}${branchName}")
        }

        println("adding job $baseJobName-$branchName")
        post('createItem', config, [name: "${baseJobName}-${branchName}"], ContentType.XML)
    }

    void deleteJob(String jobName) {
        println "deleting job $jobName"
        post("job/${jobName}/doDelete")
    }

    void createViewForBranch(String baseName, String branchName) {
        String viewName = "$baseName-$branchName"
        Map body = [name: viewName, mode: 'hudson.model.ListView', Submit: 'OK', json: '{"name": "' + viewName + '", "mode": "hudson.model.ListView"}']
        println "creating view ${viewName}"
        post('createView', body)

        body = [useincluderegex: 'on', includeRegex: "BuildTripleMap*.*${branchName}", name: viewName, json: '{"name": "' + viewName + '","useincluderegex": {"includeRegex": "BuildTripleMap*.*' + branchName + '"},' + VIEW_COLUMNS_JSON + '}']

        println "configuring view ${viewName}"
        post("view/${viewName}/configSubmit", body)

    }

    List getViews() {
        println "getting views"
        def response = restClient.get(path: 'api/json', query: [tree:'views[name,jobs[name]]'])
        response.data.views
    }

    void deleteView(String viewName) {
        println "deleting view"
        post("view/${viewName}/doDelete")
    }

    /**
     * @author Kelly Robinson
     * from https://github.com/kellyrob99/Jenkins-api-tour/blob/master/src/main/groovy/org/kar/hudson/api/PostRequestSupport.groovy
     */
    private def post(String path, postBody = [:], params = [:], ContentType contentType = ContentType.URLENC) {
        HTTPBuilder http = new HTTPBuilder(jenkinsServerUrl)
        def status
        http.handler.failure = { resp ->
            println "Unexpected failure on $jenkinsServerUrl$path: ${resp.statusLine} ${resp.status}"
            status = resp.statusLine.statusCode
        }

        http.post(path: path, body: postBody, query: params,
                requestContentType: contentType) { resp ->
            assert resp.statusLine.statusCode < 400
            status = resp.statusLine.statusCode
        }
        status
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
