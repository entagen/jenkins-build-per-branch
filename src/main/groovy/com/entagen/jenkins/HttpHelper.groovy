package com.entagen.jenkins

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.RESTClient
import org.apache.http.HttpRequest
import org.apache.http.HttpRequestInterceptor
import org.apache.http.HttpStatus
import org.apache.http.client.HttpResponseException
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.protocol.HttpContext

class HttpHelper {

    HttpRequestInterceptor requestInterceptor
    RESTClient restClient
    String serverUrl
    boolean findCrumb = true
    def crumbInfo

    public void addBasicAuth(String serverUser, String serverPassword) {
        println "use basic authentication"

        this.requestInterceptor = new HttpRequestInterceptor() {
            void process(HttpRequest httpRequest, HttpContext httpContext) {
                def auth = serverUser + ':' + serverPassword
                httpRequest.addHeader('Authorization', 'Basic ' + auth.bytes.encodeBase64().toString())
            }
        }

        this.restClient.client.addRequestInterceptor(this.requestInterceptor)
    }

    public get(Map map) {
        if(restClient == null) {
            serverUrl = serverUrl ?: map.serverUrl
            if(!serverUrl) {
                throw new UnknownHostException("Null host")
            }
            restClient = new RESTClient(map.serverUrl ?: this.serverUrl)
        }

        // get is destructive to the map, if there's an error we want the values around still
        Map mapCopy = map.clone() as Map
        def response

        assert mapCopy.path != null, "'path' is a required attribute for the GET method"

        try {
            response = restClient.get(map)
        } catch (HttpHostConnectException ex) {
            println "Unable to connect to host: $serverUrl"
            throw ex
        } catch (UnknownHostException ex) {
            println "Unknown host: $serverUrl"
            throw ex
        } catch (HttpResponseException ex) {
            def message = "Unexpected failure with path $serverUrl${mapCopy.path}, HTTP Status Code: ${ex.response?.status}, full map: $mapCopy"
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

        //Added the support for jenkins CSRF option, this could be changed to be a build flag if needed.
        //http://jenkinsurl.com/crumbIssuer/api/json  get crumb for csrf protection  json: {"crumb":"c8d8812d615292d4c0a79520bacfa7d8","crumbRequestField":".crumb"}
        if (findCrumb) {
            findCrumb = false
            println "Trying to find crumb: ${serverUrl}crumbIssuer/api/json"
            try {
                def response = restClient.get(path: "crumbIssuer/api/json")

                if (response.data.crumbRequestField && response.data.crumb) {
                    crumbInfo = [:]
                    crumbInfo['field'] = response.data.crumbRequestField
                    crumbInfo['crumb'] = response.data.crumb
                }
                else {
                    println "Found crumbIssuer but didn't understand the response data trying to move on."
                    println "Response data: " + response.data
                }
            }
            catch (HttpResponseException e) {
                if (e.response?.status == 404) {
                    println "Couldn't find crumbIssuer for jenkins. Just moving on it may not be needed."
                }
                else {
                    def msg = "Unexpected failure on ${serverUrl}crumbIssuer/api/json: ${resp.statusLine} ${resp.status}"
                    throw new Exception(msg)
                }
            }
        }

        if (crumbInfo) {
            params[crumbInfo.field] = crumbInfo.crumb
        }

        HTTPBuilder http = new HTTPBuilder(serverUrl)

        if (requestInterceptor) {
            http.client.addRequestInterceptor(this.requestInterceptor)
        }

        Integer status = HttpStatus.SC_EXPECTATION_FAILED

        http.handler.failure = { resp ->
            println resp.data
            def msg = "Unexpected failure on $serverUrl$path: ${resp.statusLine} ${resp.status}"
            status = resp.statusLine.statusCode
            throw new Exception(msg)
        }

        http.post(path: path, body: postBody, query: params,
                requestContentType: contentType) { resp ->
            assert resp.statusLine.statusCode < 400
            status = resp.statusLine.statusCode
        }
        return status
    }

}
