Jenkins Build Per Branch
========================

The code in this repo lets you automatically generate Jenkins jobs for new branches in a specified repository, using templates to create the jobs.

Using it is as easy as getting the set of jobs for your master branch working as you want them to.  Then create a new job that is in charge of creating new builds for each new branch in jenkins using the code in this repo.  That job monitors github for new branches that we don't already have jobs for.

It also automatically cleans up old feature branches that have been fully merged into another branch.

Installation
------------

It has the following plugin requirements in Jenkins:

* [Jenkins Git Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin) - currently called the "Jenkins GIT plugin" in the UI as of plugin version 1.1.17
* [Gradle Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Groovy+plugin) - currently called the "Hudson Groovy builder" in the UI as of plugin version 1.12
* the git command line app also must be installed (this is already a requirement of the Jenkins Git Plugin), and it must be configured with credentials (likely an SSH key) that has authorization rights to the git repository


Conventions
-----------

It is expected that there will be 1 or more "template" jobs that will be replicated for each new branch that is created.  In most workflows, this will likely be the jobs for the `master` branch.

The job names are expected to be of the format:

    <templateJobPrefix>-<jobName>-<templateBranchName>

Where:

* `templateJobPrefix` is probably the name of the git repository, ex: `myproject`
* `jobName` describes what Jenkins is actually doing with the job, ex: `runTests`
* `templateBranchName` is the branch that's being used as a template for the feature branch jobs, ex: `master`

So you could have 3 jobs that are dependent on each other to run, ex:

* MyProject-RunTests-master
* MyProject-BuildWar-master
* MyProject-DeployApp-master

If you created a new feature branch (ex: `newfeature`) and pushed it to the main git repo, it would create these jobs in Jenkins:

* MyProject-RunTests-newfeature
* MyProject-BuildWar-newfeature
* MyProject-DeployApp-newfeature

Once `newfeature` was tested, accepted, and merged into master, the sync job would then delete those jobs on the next run.

What Kinds of Worflows Need a Build Per Branch?
-----------------------------------------------

This can be very useful if your team is using a workflow like [Github Flow](http://scottchacon.com/2011/08/31/github-flow.html).

In this workflow:

* the `master` branch is always deployable
* New work is _always_ done in a feature branch (i.e. `ted/my_fancy_feature`) and never committed directly to `master`  
* When a feature is finished and ready, a pull request is made against master that is reviewed and tested by at least one other developer 
* After a pull request is approved, the feature branch is merged to `master` and the feature branch is then deleted
* master should then be deployed as soon as possible

The advantages to this model are:

* all code is seen (and tested) by at least one other person
* Feature branches are very short-lived (mostly a day or two) and rarely get out of date making the majority of merges trivial
* new features are quickly available to end users, and can be quickly fixed if there is some sort of issue

One disadvantage is that with multiple, short-lived branches, manually creating CI to confirm that all tests continue to pass becomes far too much overhead.  It must be automated for this model to work and be trusted.  "Jenkins Build Per Branch" is the answer to that problem.


Known Limitations
-----------------

The current version has these known limitations:

* branches created from old code that don't fit the way the template currently works will fail.
* 


Potential Future Enhancements
-----------------------------

* allow a git repo to hold your job definitions, it would then be a submodule of the repo under test.  As the jenkins configuration changes to meet the needs of the repo under test, the job definition repo could be updated appropriately.  This would tie a particular SHA in the job definition repo to the current branch so Jenkins always has the right job definitions for building.

* make this into a plugin with a new jenkins "job type" that simplifies things down to the subset of what we actually want
