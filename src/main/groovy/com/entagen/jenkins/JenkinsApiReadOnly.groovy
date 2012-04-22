package com.entagen.jenkins

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.RESTClient
import org.apache.http.client.HttpResponseException
import org.apache.http.conn.HttpHostConnectException

import static groovyx.net.http.ContentType.TEXT
import org.apache.http.HttpStatus

class JenkinsApiReadOnly extends JenkinsApi {

    @Override
    protected Integer post(String path, postBody = [:], params = [:], ContentType contentType = ContentType.URLENC) {
        println "READ ONLY! skipping POST to $path"
        // we never want to post anything with a ReadOnly API, just return OK for all requests to it
        return HttpStatus.SC_OK
    }
}
