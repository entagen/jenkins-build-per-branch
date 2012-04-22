package com.entagen.jenkins

import org.junit.Test
import org.apache.http.HttpStatus

class JenkinsApiReadOnlyTests extends GroovyTestCase {
    @Test public void testAllReadOnlyPostsReturnOK() {
        JenkinsApi api = new JenkinsApiReadOnly(jenkinsServerUrl: "http://localhost:9090/jenkins")
        assert api.post("http://foo.com") == HttpStatus.SC_OK
    }
}
