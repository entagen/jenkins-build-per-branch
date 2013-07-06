package com.entagen.jenkins

import org.junit.Test
import groovy.mock.interceptor.MockFor
import org.apache.http.client.HttpResponseException
import groovyx.net.http.RESTClient
import net.sf.json.JSON
import net.sf.json.JSONObject

class JenkinsApiSSLTest extends GroovyTestCase {

    private String getJenkinsServerUrl(){
        return System.getProperty("jenkinsUrl") ?: "http://localhost:9090/jenkins"
    }

    @Test public void testGetJobNames() {
        JenkinsApi api = new JenkinsApi(jenkinsServerUrl: getJenkinsServerUrl())
        api.allowSelfsignedSslCerts()
        api.getJobNames()
    }

    @Test public void testGetJobNamesWithoutSelfsignedSslCerts() {
        JenkinsApi api = new JenkinsApi(jenkinsServerUrl: getJenkinsServerUrl())
        if(jenkinsServerUrl.startsWith("https")){
            assert "peer not authenticated" == shouldFail {
                api.getJobNames("myproj")
            }
        }
    }
}

