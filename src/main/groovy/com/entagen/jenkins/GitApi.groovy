package com.entagen.jenkins

import groovyx.net.http.RESTClient
import org.apache.http.client.HttpResponseException
import org.apache.http.conn.HttpHostConnectException

import java.util.regex.Pattern

class GitApi extends BranchSource {
    String gitUrl

    public List<ConcreteJob> getBranchJobs(List<TemplateJob> templates) {
        List<ConcreteJob> jobs = []
        List<String> branchNames = getBranchNames()
        for(template in templates) {
            for(branchName in branchNames) {
                if(branchName != template.templateBranchName) {
                    jobs << getBranchJob(branchName, template)
                }
            }
        }
        return jobs
    }

    ConcreteJob getBranchJob(String branchName, TemplateJob template) {
        // git branches often have a forward slash in them, but they make jenkins cranky, turn it into an underscore
        String jobName = template.jobNameForBranch(branchName)
        return new ConcreteJob(templateJob: template, branchName: branchName, jobName: jobName, config: template.config)
    }

    public List<String> getBranchNames() {
        String command = "git ls-remote --heads ${gitUrl}"
        List<String> branchNames = []

        eachResultLine(command) { String line ->
            println "\t$line"
            // lines are in the format of: <SHA>\trefs/heads/BRANCH_NAME
            // ex: b9c209a2bf1c159168bf6bc2dfa9540da7e8c4a26\trefs/heads/master
            String branchNameRegex = "^.*\trefs/heads/(.*)\$"
            String branchName = line.find(branchNameRegex) { full, branchName -> branchName }
            if (passesFilter(branchName)) branchNames << branchName
        }

        return branchNames
    }

    // assumes all commands are "safe", if we implement any destructive git commands, we'd want to separate those out for a dry-run
    public void eachResultLine(String command, Closure closure) {
        println "executing command: $command"
        def process = command.execute()
        def inputStream = process.getInputStream()
        def gitOutput = ""

        while(true) {
          int readByte = inputStream.read()
          if (readByte == -1) break // EOF
          byte[] bytes = new byte[1]
          bytes[0] = readByte
          gitOutput = gitOutput.concat(new String(bytes))
        }
        process.waitFor()

        if (process.exitValue() == 0) {
            gitOutput.eachLine { String line ->
               closure(line)
          }
        } else {
            String errorText = process.errorStream.text?.trim()
            println "error executing command: $command"
            println errorText
            throw new Exception("Error executing command: $command -> $errorText")
        }
    }
}
