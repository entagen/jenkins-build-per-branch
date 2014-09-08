package com.entagen.jenkins

import java.util.regex.Pattern
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.*;
//import org.jvnet.hudson.test.HudsonTestCase;

class JenkinsJobManager {
    String templateJobPrefix
    String templateBranchName
    String gitUrl
    String nestedView
    String jenkinsUrl
    String branchNameRegex
    String jenkinsUser
    String jenkinsPassword
    String test;
    
    Boolean dryRun = false
    Boolean noViews = false
    Boolean noDelete = false
    Boolean startOnCreate = false

    JenkinsApi jenkinsApi
    GitApi gitApi


    public void restartJenkins()
    {
        Process p = Runtime.getRuntime().exec("sudo /etc/init.d/jenkins restart");
        Thread.sleep(5000);

    }
    public String readFile(String filePath) {
        String result = "";
        try {

            BufferedReader bufferedReader = new BufferedReader(new FileReader(
                    filePath));
            String line = "";

            while ((line = bufferedReader.readLine()) != null) {
                result = result + line + "\n";
            }

        } catch (Exception e) {

        }
        return result;
    }

    public void config(String filePath, String pattern, String toInsert) {
        // now edit the file
        // now add the first part
        // find the <builders> tag

        try {
            FileReader fileReader = new FileReader(filePath);
            String line = "";
            int count = 0;
            boolean end = false;
            String prefix = "";
            String suffix = "";
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            // line.con
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains(pattern)) {
                    end = true;
                    prefix = prefix + line + "\n";
                    continue;
                }
                if (end)
                    suffix = suffix + line + "\n";
                else
                    prefix = prefix + line + "\n";

            }
            System.out.println("prefix" + prefix);
            System.out.println("toinsert" + toInsert);
            System.out.println("suffix" + suffix);
            FileWriter fileWriter = new FileWriter(filePath);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(prefix + toInsert + suffix);
            bufferedWriter.close();

        } catch (Exception e) {

        }

        // then add the line f

    }


    JenkinsJobManager(Map props) {
        for (property in props) {
            this."${property.key}" = property.value
        }
        initJenkinsApi()
        initGitApi()
        testFunction()


    }



    public void createOrg(String rootFolder, String org) {
        // need to create a nestedType view in org
        // first find the org
        // then <views>
        // then add the code
        String fileRead=readFile("/d0/jenkins/config.xml");
        String toInsert=" <hudson.plugins.nested__view.NestedView>\n" +
                "          <owner class=\"hudson.plugins.nested_view.NestedView\" reference=\"../../..\"/>\n" +
                "          <name>"+org+"</name>\n" +
                "          <filterExecutors>false</filterExecutors>\n" +
                "          <filterQueue>false</filterQueue>\n" +
                "          <properties class=\"hudson.model.View\$PropertyList\"/>\n" +
                "          <views/>\n" +
                "          <columns>\n" +
                "            <columns/>\n" +
                "          </columns>\n" +
                "        </hudson.plugins.nested__view.NestedView>";
       if(!fileRead.contains(org)) {
           config("/d0/jenkins/config.xml", rootFolder, toInsert);
           restartJenkins();
       }

        //for this we need to write the file handing program


    }
    public void createRepo(String rootFolder, String org, String repoName) {
        // need to create listView in org
        // this can be done with existing functions
        createOrg(rootFolder,org);
        jenkinsApi.createView(repoName,rootFolder,org);



    }




    public void testFunction() {
        System.out.println(jenkinsApi.getJobNames("Vivek"));
        createRepo("nestedtype_git","nested_org3","testrepo1");

       // System.out.println(jenkinsApi.getJobConfig("VivekTestSyncYOURPROJECTGitBranchesWithJenkins"));
        //jenkinsApi.cre
       // jenkinsApi.startJob("VivekTestSyncYOURPROJECTGitBranchesWithJenkins");
       // BranchView branchView = new BranchView("","GraphiteDasboards/tree/master");
       //jenkinsApi.createViewForBranch(branchView,"");
       // jenkinsApi.createViewForBranch("branchView");
       // jenkinsApi.createView("org3");
        //println(jenkinsApi.getViewNames("test"));

        //WebClient wc = new WebClient();

    }

    void syncWithRepo() {
        List<String> allBranchNames = gitApi.branchNames
        List<String> allJobNames = jenkinsApi.jobNames

        // ensure that there is at least one job matching the template pattern, collect the set of template jobs
        List<TemplateJob> templateJobs = findRequiredTemplateJobs(allJobNames)

        // create any missing template jobs and delete any jobs matching the template patterns that no longer have branches
        syncJobs(allBranchNames, allJobNames, templateJobs)

        // create any missing branch views, scoped within a nested view if we were given one
        if (!noViews) {
            syncViews(allBranchNames)
        }
    }

    public void syncJobs(List<String> allBranchNames, List<String> allJobNames, List<TemplateJob> templateJobs) {
        List<String> currentTemplateDrivenJobNames = templateDrivenJobNames(templateJobs, allJobNames)
        List<String> nonTemplateBranchNames = allBranchNames - templateBranchName
        List<ConcreteJob> expectedJobs = this.expectedJobs(templateJobs, nonTemplateBranchNames)

        createMissingJobs(expectedJobs, currentTemplateDrivenJobNames, templateJobs)
        if (!noDelete) {
            deleteDeprecatedJobs(currentTemplateDrivenJobNames - expectedJobs.jobName)
        }
    }

    public void createMissingJobs(List<ConcreteJob> expectedJobs, List<String> currentJobs, List<TemplateJob> templateJobs) {
        List<ConcreteJob> missingJobs = expectedJobs.findAll { !currentJobs.contains(it.jobName) }
        if (!missingJobs) return

        for(ConcreteJob missingJob in missingJobs) {
            println "Creating missing job: ${missingJob.jobName} from ${missingJob.templateJob.jobName}"
            jenkinsApi.cloneJobForBranch(missingJob, templateJobs)
            if (startOnCreate) {
                jenkinsApi.startJob(missingJob)
            }
        }

    }

    public void deleteDeprecatedJobs(List<String> deprecatedJobNames) {
        if (!deprecatedJobNames) return
        println "Deleting deprecated jobs:\n\t${deprecatedJobNames.join('\n\t')}"
        deprecatedJobNames.each { String jobName ->
            jenkinsApi.deleteJob(jobName)
        }
    }

    public List<ConcreteJob> expectedJobs(List<TemplateJob> templateJobs, List<String> branchNames) {
        branchNames.collect { String branchName ->
            templateJobs.collect { TemplateJob templateJob -> templateJob.concreteJobForBranch(branchName) }
        }.flatten()
    }

    public List<String> templateDrivenJobNames(List<TemplateJob> templateJobs, List<String> allJobNames) {
        List<String> templateJobNames = templateJobs.jobName
        List<String> templateBaseJobNames = templateJobs.baseJobName

        // don't want actual template jobs, just the jobs that were created from the templates
        return (allJobNames - templateJobNames).findAll { String jobName ->
            templateBaseJobNames.find { String baseJobName -> jobName.startsWith(baseJobName)}
        }
    }

    List<TemplateJob> findRequiredTemplateJobs(List<String> allJobNames) {
        String regex = /^($templateJobPrefix-[^-]*)-($templateBranchName)$/

        List<TemplateJob> templateJobs = allJobNames.findResults { String jobName ->
            TemplateJob templateJob = null
            jobName.find(regex) { full, baseJobName, branchName ->
                templateJob = new TemplateJob(jobName: full, baseJobName: baseJobName, templateBranchName: branchName)
            }
            return templateJob
        }

        assert templateJobs?.size() > 0, "Unable to find any jobs matching template regex: $regex\nYou need at least one job to match the templateJobPrefix and templateBranchName suffix arguments"
        return templateJobs
    }

    public void syncViews(List<String> allBranchNames) {
        List<String> existingViewNames = jenkinsApi.getViewNames(this.nestedView)
        List<BranchView> expectedBranchViews = allBranchNames.collect { String branchName -> new BranchView(branchName: branchName, templateJobPrefix: this.templateJobPrefix) }

        List<BranchView> missingBranchViews = expectedBranchViews.findAll { BranchView branchView -> !existingViewNames.contains(branchView.viewName)}
        addMissingViews(missingBranchViews)

        if (!noDelete) {
            List<String> deprecatedViewNames = getDeprecatedViewNames(existingViewNames, expectedBranchViews)
            deleteDeprecatedViews(deprecatedViewNames)
        }
    }

    public void addMissingViews(List<BranchView> missingViews) {
        println "Missing views: $missingViews"
        for (BranchView missingView in missingViews) {
            jenkinsApi.createViewForBranch(missingView, this.nestedView)
        }
    }

    public List<String> getDeprecatedViewNames(List<String> existingViewNames, List<BranchView> expectedBranchViews) {
         return existingViewNames?.findAll { it.startsWith(this.templateJobPrefix) } - expectedBranchViews?.viewName ?: []
    }

    public void deleteDeprecatedViews(List<String> deprecatedViewNames) {
        println "Deprecated views: $deprecatedViewNames"

        for(String deprecatedViewName in deprecatedViewNames) {
            jenkinsApi.deleteView(deprecatedViewName, this.nestedView)
        }

    }

    JenkinsApi initJenkinsApi() {
        if (!jenkinsApi) {
            assert jenkinsUrl != null
            if (dryRun) {
                println "DRY RUN! Not executing any POST commands to Jenkins, only GET commands"
                this.jenkinsApi = new JenkinsApiReadOnly(jenkinsServerUrl: jenkinsUrl)
            } else {
                this.jenkinsApi = new JenkinsApi(jenkinsServerUrl: jenkinsUrl)
            }

            if (jenkinsUser || jenkinsPassword) this.jenkinsApi.addBasicAuth(jenkinsUser, jenkinsPassword)
        }

        return this.jenkinsApi
    }

    GitApi initGitApi() {
        if (!gitApi) {
            assert gitUrl != null
            this.gitApi = new GitApi(gitUrl: gitUrl)
            if (this.branchNameRegex){
                this.gitApi.branchNameFilter = ~this.branchNameRegex
            }
        }

        return this.gitApi
    }
}
