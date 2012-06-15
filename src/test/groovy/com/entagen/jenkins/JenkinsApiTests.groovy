package com.entagen.jenkins

import org.junit.Test
import groovy.mock.interceptor.MockFor
import org.apache.http.client.HttpResponseException
import groovyx.net.http.RESTClient
import net.sf.json.JSON
import net.sf.json.JSONObject

class JenkinsApiTests extends GroovyTestCase {

    @Test public void testInvalidHostThrowsConnectionException() {
        JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://invalid.foo:9090/jenkins")
        assert "invalid.foo" == shouldFail {
            api.getJobNames("myproj")
        }
    }

    @Test public void testCantConnectToEndpointThrowsException() {
        JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:12345/jenkins")
        assert "Connection to http://localhost:12345 refused" == shouldFail {
            api.getJobNames("myproj")
        }

    }

    @Test public void test404ThrowsException() {
        MockFor mockRESTClient = new MockFor(RESTClient)
        mockRESTClient.demand.get { Map<String,?> args ->
            def ex = new HttpResponseException(404, "Not Found")
            ex.metaClass.getResponse = {-> [ status: 404 ] }
            throw ex
        }

        mockRESTClient.use {
            JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/goodHostAndPortBadUrl")
            assert "Unexpected failure with path http://localhost:9090/goodHostAndPortBadUrl/api/json, HTTP Status Code: 404, full map: [path:api/json]" == shouldFail() {
                api.getJobNames("myproj")
            }
        }
    }

    @Test public void testGetJobNames_matchPrefix() {
        JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/jenkins")

        Map json = [
                jobs: [
                        [name: "myproj-FirstJob"],
                        [name: "otherproj-SecondJob"]
                ]
        ]
        withJsonResponse(json) {
            List<String> projectNames = api.getJobNames("myproj")
            assert projectNames == ["myproj-FirstJob"]
        }
    }

    @Test public void testGetJobNames_noPrefix() {
        JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/jenkins")

        Map json = [
                jobs: [
                        [name: "myproj-FirstJob"],
                        [name: "otherproj-SecondJob"]
                ]
        ]
        withJsonResponse(json) {
            List<String> projectNames = api.jobNames
            assert projectNames.sort() == ["myproj-FirstJob", "otherproj-SecondJob"]
        }
    }

    public void withJsonResponse(Map toJson, Closure closure) {
        JSON json = toJson as JSONObject
        MockFor mockRESTClient = new MockFor(RESTClient)
        mockRESTClient.demand.get { Map<String, ?> args ->
            return [data: json]
        }

        mockRESTClient.use {
            closure()
        }
    }


}

