package com.entagen.jenkins

import groovyx.net.http.ContentType

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
    String jobPrefix;
    String userProfile;
    String mavenCmd;
    String emailId;
    String businessVertical;
    String team;

    Boolean dryRun = false
    Boolean noViews = false
    Boolean noDelete = false
    Boolean startOnCreate = false

    JenkinsApi jenkinsApi
    GitApi gitApi
    String repo;
    String org;
    String rootFolder = "Git-Structure";

void getJenkinsPassword() {

        try {

            BufferedReader bufferedReaderpassword = new BufferedReader(new FileReader(
                    "/d0/jenkins/job_generator_cred"));
            jenkinsPassword = bufferedReaderpassword.readLine();


        } catch (Exception e) {
            System.out.println(e);

        }

    }

    JenkinsJobManager(Map props) {
        for (property in props) {
            this."${property.key}" = property.value
        }
        getJenkinsPassword();
        initJenkinsApi()
        initGitApi()
        org = getOrg();
        repo = getRepo();
        println "org+repo" + org + repo;
       // createJobsForallRepo();
        createJobsForallBranches();

    }


    public void createNestedViewOrg(String rootFolder) {
        println "org is" + org;

        String filename = "/tmp/config.xml";
        filename = "/d0/jenkins/config.xml";
        String fileRead = readFile(filename);
        String toInsert = "<hudson.plugins.nested__view.NestedView>\n" +
                "          <owner class=\"hudson.plugins.nested_view.NestedView\" reference=\"../../..\"/>\n" +
                "          <name>" + org + "</name>\n" +
                "          <filterExecutors>false</filterExecutors>\n" +
                "          <filterQueue>false</filterQueue>\n" +
                "          <properties class=\"hudson.model.View\$PropertyList\"/>\n" +
                "          <views/>\n" +
                "          <columns>\n" +
                "            <columns/>\n" +
                "          </columns>\n" +
                "</hudson.plugins.nested__view.NestedView>\n";
        println "configfile" + fileRead;
        if (!fileRead.contains("<name>" + org + "</name>")) {
            println "creating org =>" + org;
            config(filename, rootFolder, "<views>", toInsert);
            reload();


        }

    }

    public void createRepoView(String rootFolder, String org, String repoName) {
        // need to create listView in org
        // this can be done with existing functions
        // createOrg(rootFolder,org);
        // restartJenkins();
        jenkinsApi.createView(repoName, rootFolder, org);
        //reload();
        sleep(10000);


    }


    void createJob(String jobName, String jobTemplate) {

    }

    HashSet<String> createJobSet(List<String> jobs) {
        HashSet<String> uniqueJobs = new HashSet<String>();
        for (int i = 0; i < jobs.size(); i++) {
            String jobName = jobs.get(i);
            uniqueJobs.add(jobName.toUpperCase());
        }
        return uniqueJobs;
    }

    public void createJobsForallBranches() {


        List<String> jobList = jenkinsApi.getJobNames("");
        System.out.println("userprofile:" + userProfile);
        System.out.println("mavenCmd" + mavenCmd);
        System.out.println("emailid" + emailId + "businessVertical=>" + businessVertical + "team=>" + team);
        HashSet<String> uniqueJobs = createJobSet(jobList);
        // for(int i=0;i<jo)
        createNestedViewOrg(rootFolder);
        if (!checkRepoPresent()) {
            println "creating repo";

            createRepoView("Git-Structure", getOrg(), getRepo());
        }

        List<String> branchNameList = gitApi.getBranchNames();
        //  println jenkinsApi.getJobConfig("sandbox-cyclops-Dev_job-develop");
        String config = jenkinsApi.getJobConfig(templateJobPrefix);

        String jobName;


        for (int i = 0; i < branchNameList.size(); i++) {
            String branchName = branchNameList.get(i);

            jobName = getOrg() + "_" + getRepo() + "_" + branchName.replaceAll('/', '_');


            if (!jobList.contains(jobName) && !uniqueJobs.contains(jobName.toUpperCase())) {

                uniqueJobs.add(jobName.toUpperCase());


                emailId = emailId.replace(',', ' ');
                mavenCmd = mavenCmd.replace(',', ' ');

                config = jenkinsApi.getJobConfig(templateJobPrefix);
                config = config.replace("GIT_URL", gitUrl);
                config = config.replace("UserProfile_value", userProfile);
                config = config.replace("Maven_CMD_value", mavenCmd);
                config = config.replace("Maven_CMD_value", mavenCmd);
                config = config.replace("EmailIds_value", emailId);
                config = config.replace("Team_value", team);
                config = config.replace("Business_Vertical_value", businessVertical);
                config = config.replace("Team_value", team);
                config = config.replace("Business_Vertical_value", businessVertical);
                config = config.replace("BranchName", branchName);
                println "creating job =>" + jobName;
                jenkinsApi.post(jenkinsApi.buildJobPath("createItem", rootFolder, getOrg(), getRepo()), config, [name: jobName, mode: 'copy', from: templateJobPrefix], ContentType.XML)
                jenkinsApi.post('job/' + jobName + "/config.xml", config, [:], ContentType.XML)
                // break;
            }


        }


    }

    void syncWithRepo() {
        List<String> allBranchNames = gitApi.branchNames
        List<String> allJobNames = jenkinsApi.jobNames

        // ensure that there is at least one job matching the template pattern, collect the set of template jobs
        List<TemplateJob> templateJobs = findRequiredTemplateJobs(allJobNames, templateJobPrefix)

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
        println 'currentTemplateDrivenJobNames';
        println currentTemplateDrivenJobNames;
        println 'nonTemplateBranchNames';
        println nonTemplateBranchNames;
        println 'expectedJobs';
        println expectedJobs;

        /* createMissingJobs(expectedJobs, currentTemplateDrivenJobNames, templateJobs)
         if (!noDelete) {
             deleteDeprecatedJobs(currentTemplateDrivenJobNames - expectedJobs.jobName)
         }*/
    }

    public void createMissingJobs(List<ConcreteJob> expectedJobs, List<String> currentJobs, List<TemplateJob> templateJobs) {
        List<ConcreteJob> missingJobs = expectedJobs.findAll { !currentJobs.contains(it.jobName) }
        if (!missingJobs) return

        for (ConcreteJob missingJob in missingJobs) {
            println "Creating missing job: ${missingJob.jobName} from ${missingJob.templateJob.jobName}"
            jenkinsApi.cloneJobForBranch(missingJob, templateJobs, rootFolder, getOrg(), getRepo());
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
            templateBaseJobNames.find { String baseJobName -> jobName.startsWith(baseJobName) }
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


    List<TemplateJob> findRequiredTemplateJobs(List<String> allJobNames, String templateJobName) {
        String regex = /^($templateJobPrefix-[^-]*)-($templateBranchName)$/
        regex = ~/$templateJobPrefix/;


        List<TemplateJob> templateJobs = allJobNames.findResults { String jobName ->
            TemplateJob templateJob = null
            jobName.find(regex)
                    { full, baseJobName, branchName ->
                        templateJob = new TemplateJob(jobName: full, baseJobName: baseJobName, templateBranchName: branchName)
                    }
            return templateJob
        }

        /*
          for(int i=0;i<allJobNames.size();i++) {
              if(allJobNames.get(i).contains(templateJobName)) templateJobs.add(allJobNames.get(i));


          }*/
        assert templateJobs?.size() > 0, "Unable to find any jobs matching template regex: $regex\nYou need at least one job to match the templateJobPrefix and templateBranchName suffix arguments"
        return templateJobs
    }


    public void syncViews(List<String> allBranchNames) {
        List<String> existingViewNames = jenkinsApi.getViewNames(this.nestedView)
        List<BranchView> expectedBranchViews = allBranchNames.collect { String branchName -> new BranchView(branchName: branchName, templateJobPrefix: this.templateJobPrefix) }

        List<BranchView> missingBranchViews = expectedBranchViews.findAll { BranchView branchView -> !existingViewNames.contains(branchView.viewName) }
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
        return existingViewNames?.findAll {
            it.startsWith(this.templateJobPrefix)
        } - expectedBranchViews?.viewName ?: []
    }

    public void deleteDeprecatedViews(List<String> deprecatedViewNames) {
        println "Deprecated views: $deprecatedViewNames"

        for (String deprecatedViewName in deprecatedViewNames) {
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
            if (this.branchNameRegex) {
                this.gitApi.branchNameFilter = ~this.branchNameRegex
            }
        }

        return this.gitApi
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

    public void config(String filePath, String pattern1, String pattern2, String toInsert) {
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
            String last = "";


            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains(pattern1)) {
                    if ((last.trim()).compareTo((line.trim())) != 0) {


                        prefix = prefix + line + "\n";


                    }
                    last = line;
                    break;
                } else {
                    if (last.trim().compareTo(line.trim()) != 0) {
                        prefix = prefix + line + "\n";

                    }
                    last = line;
                }
            }
            boolean firstTime = true;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains(pattern2) && firstTime) {
                    end = true;

                    firstTime = false;
                    if (last.trim().compareTo(line.trim()) != 0) {
                        prefix = prefix + line + "\n";


                    }
                    last = line;
                    continue;
                }
                if (end) {
                    if (last.trim().compareTo(line.trim()) != 0) {
                        suffix = suffix + line + "\n";

                    }
                    last = line;
                } else {
                    if (last.trim().compareTo(line.trim()) != 0) {
                        prefix = prefix + line + "\n";

                    }
                    last = line;
                }

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
    String getOrg() {
        String git = gitUrl.substring(12);

        int first = git.indexOf('/');
        int second = git.indexOf('/', first + 1);
        println "org" + org;

        return git.substring(first + 1, second);


    }

    String getRepo() {
        println "gitul" + gitUrl;
        String git = gitUrl.substring(12);
        int first = git.indexOf('/');
        int second = git.indexOf('/', first + 1);
        println "repo" + repo;
        return git.substring(second + 1, git.length());

    }

    boolean createJobsForallRepo() {

        /*String url=jenkinsUrl+"view/Git-Structure/view/"+getOrg()+"/view/"+getRepo();
        System.out.println("checking path => "+ "view/Git-Structure/view/"+getOrg()+"/view/"+getRepo());

        try {

            String testurl=jenkinsUrl+"view/Git-Structure/view/"+getOrg()+"/view/"+getRepo()+"/newJob";
            URL u = new URL(testurl);
            HttpURLConnection huc = (HttpURLConnection) u.openConnection();

            huc.setRequestMethod("HEAD");
            if (huc.getResponseCode() != HttpURLConnection.HTTP_OK) {
                System.out.println("response code  => " + huc.getResponseCode() + " not found " + url);
                return false;
            } else {
                System.out.println("response code => " + huc.getResponseCode() + " " + url);
                return true;
            }
        } catch (Exception e) {
            System.out.println(e);
            return false;

        }
*/

        String path1 = "view/Git-Structure/view/" + getOrg() + "/view/" + getRepo();
//   String path = 'view/Git-Structure/view/' + getOrg() + '/view/' + getRepo();
        boolean response = jenkinsApi.getCheck(path: path1)
        return response;


    }

    public void restartJenkins() {
        Process p = Runtime.getRuntime().exec("sudo /etc/init.d/jenkins restart");
        //  Thread.sleep(5000);

    }

    public void reload() {
        // Process p = Runtime.getRuntime().exec("sudo /etc/init.d/jenkins restart");
        println "sorry guys I am reloading :(";
        jenkinsApi.post('reload/');
        sleep(60000);
        println "finally reloaded :)";

        // Thread.sleep(5000);


    }


}
