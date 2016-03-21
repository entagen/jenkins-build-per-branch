package com.entagen.jenkins

import groovyx.net.http.ContentType
import org.apache.http.HttpStatus

class JenkinsApiReadOnly extends JenkinsApi {

    @Override
    protected Integer post(String path, postBody = [:], params = [:], ContentType contentType = ContentType.URLENC) {
        println "READ ONLY! skipping POST to $path with params: ${params}, postBody:\n$postBody"
        // we never want to post anything with a ReadOnly API, just return OK for all requests to it
        return HttpStatus.SC_OK
    }
}
