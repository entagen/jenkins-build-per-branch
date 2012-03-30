import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.ContentType.TEXT

@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.5.2')

def cli = new CliBuilder(usage: "${getClass().getName()} [options]", header:'Options:')
cli.j(longOpt:'jenkins-url', args:1, argName:'jenkinsUrl', "Jenkins URL")
cli.p(longOpt:'job-prefix', args:1, argName:'jobPrefix', "Template Job Prefix")
cli.s(longOpt:'job-suffix', args:1, argName:'jobSuffix', "Template Job Suffix")
cli.u(longOpt:'git-url', args:1, argName:'gitUrl', "Git Repository URL")
cli.b(longOpt:'base-name', args:1, argName:'baseName', "Base Name - View Prefix")
cli.v(longOpt:'use-nested-view', args:1, argName:'parentViewName', "Parent View Name")

def options = cli.parse(args)

String templateJobPrefix = options.p ?: 'BuildTripleMap'
String templateJobSuffix = options.s ?: 'master'
String gitUrl = options.u ?: 'git@github.com:entagen/triplemap.git'
String baseName = options.b ?: 'TM'
String parentViewName = options.v ?: 'TripleMap-feature-branches'
String jenkinsUrl = options.j ?: 'http://macallan:8081/'

println "Using jenkins @ ${jenkinsUrl}"
JenkinsApi api = new JenkinsApi(jenkinsUrl)

Set currentBuilds = api.getProjectNames(templateJobPrefix + '-')

Set templateProjectNames = currentBuilds.findAll { it.startsWith(templateJobPrefix + '-') && it.endsWith('-' + templateJobSuffix) }
Set baseTemplateProjectNames = templateProjectNames.collect {(it.minus('-' + templateJobSuffix))}

String branchesCommand = "git ls-remote --heads ${gitUrl}"

Map<String, String> branches = [:]
def process = branchesCommand.execute()
process.waitFor()

if (process.exitValue() == 0){
    process.in.text.eachLine {String line ->
        String branchName = line.substring(line.indexOf('refs/heads/')) - 'refs/heads/'
        if (branchName != templateJobSuffix) {
            branches[branchName] = branchName.replaceAll('/', '_')
        }
    }
} else {
    println "error executing branches command: $branchesCommand"
    println process.errorStream.text
    return
}

List views = api.getViews(parentViewName)
Set viewNames = views.collect {it.name}
println "viewNames: ${viewNames}"

for (String branchName in branches.keySet()) {
    String branchDisplayName = branches[branchName]
    for (String baseTemplateName in baseTemplateProjectNames.sort()) {
        if (!currentBuilds.contains("$baseTemplateName-${branchDisplayName}".toString())) {
            api.copyJobAndSetBranch(baseTemplateName, branchName, branchDisplayName, templateJobSuffix, templateProjectNames)
        }
    }

    if (!viewNames.contains("$baseName-$branchDisplayName".toString())) {
        api.createViewForBranch(baseName, branchDisplayName, templateJobPrefix, parentViewName)
    }
}

Set expectedBuilds = branches.values().collect {branchDisplayName -> baseTemplateProjectNames.collect {it + "-${branchDisplayName}"} }.flatten()
Set buildsToDelete = currentBuilds - expectedBuilds - templateProjectNames
println 'toDelete: ' + buildsToDelete

buildsToDelete.each {
    api.deleteJob(it, parentViewName)
}

// Delete views that don't have jobs
views.each { view ->
    boolean matchesBaseName = view.name.startsWith(baseName + "-")
    if (!view.jobs && matchesBaseName) {
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

    void copyJobAndSetBranch(String baseJobName, String branchName, String branchDisplayName, String templateBranch, Set templateJobNames) {
        String config = getJobConfig("$baseJobName-$templateBranch")
        config = config.replaceAll(">origin/${templateBranch}<", ">origin/${branchName}<")

        templateJobNames.each {String templateJobName ->
            String baseName = templateJobName - templateBranch
            config = config.replaceAll(templateJobName, "${baseName}${branchDisplayName}")
        }

        println("adding job $baseJobName-$branchDisplayName")
        post('createItem', config, [name: "${baseJobName}-${branchDisplayName}"], ContentType.XML)
    }

    void deleteJob(String jobName) {
        println "deleting job $jobName"
        post("job/${jobName}/doDelete")
    }

    void createViewForBranch(String baseName, String branchName, String templateJobPrefix, String parentViewName = null) {
        String viewName = "$baseName-$branchName"
        Map body = [name: viewName, mode: 'hudson.model.ListView', Submit: 'OK', json: '{"name": "' + viewName + '", "mode": "hudson.model.ListView"}']
        println "creating view - viewName:${viewName},parentViewName:${parentViewName}"
        post("${buildViewPath(parentViewName)}/createView", body)

        body = [useincluderegex: 'on', includeRegex: "${templateJobPrefix}*.*${branchName}", name: viewName, json: '{"name": "' + viewName + '","useincluderegex": {"includeRegex": "' + templateJobPrefix + '*.*' + branchName + '"},' + VIEW_COLUMNS_JSON + '}']

        println "configuring view ${viewName}"
        post("${buildViewPath(parentViewName,viewName)}/configSubmit", body)
    }

    List getViews(String parentViewName = null) {
        def response = restClient.get(path: "${buildViewPath(parentViewName)}/api/json", query: [tree: 'views[name,jobs[name]]'])
        response.data.views
    }

    void deleteView(String viewName, String parentViewName = null) {
        println "deleting view - viewName:${viewName},parentViewName:${parentViewName}"
        post("${buildViewPath(viewName, parentViewName)}/doDelete")
    }

    private String buildViewPath(String... nestedViews) {
        List elems = nestedViews.findAll()
        elems.collect { "view/${it}" }.join('/')
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
