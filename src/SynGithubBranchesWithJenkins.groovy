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
            println("adding $baseTemplateName-$branchName")
            api.copyJobAndSetBranch(baseTemplateName, branchName, templateJobSuffix, templateProjectNames)
        }
    }

}

Set expectedBuilds = branches.collect {branch -> baseTemplateProjectNames.collect {it + "-${branch}"} }.flatten()
Set buildsToDelete = currentBuilds - expectedBuilds - templateProjectNames
println 'toDelete: ' + buildsToDelete

buildsToDelete.each {
    api.deleteJob(it)
}

class JenkinsApi {
    String jenkinsServerUrl
    def restClient

    JenkinsApi(jenkinsServerUrl) {
        this.jenkinsServerUrl = jenkinsServerUrl
        this.restClient = new groovyx.net.http.RESTClient(jenkinsServerUrl)
    }

    List<String> getProjectNames(String prefix) {
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

        post('createItem', config, [name: "${baseJobName}-${branchName}"], ContentType.XML)
    }

    void deleteJob(String jobName) {
        post("job/${jobName}/doDelete")
    }

    void createViewForBranch(String baseName, String branchName){
        post()
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


}
