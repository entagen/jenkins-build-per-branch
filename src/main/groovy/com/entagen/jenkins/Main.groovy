apply plugin: 'groovy'
apply plugin: 'idea'

repositories {
    mavenCentral()
}

dependencies {
    groovy 'org.codehaus.groovy:groovy-all:1.8.8'
    compile 'org.apache.ivy:ivy:2.2.0'
    compile 'commons-cli:commons-cli:1.2' // should be part of groovy, but not available when running for some reason
    testCompile 'junit:junit:4.10'
    compile 'org.codehaus.groovy.modules.http-builder:http-builder:0.7.1'
}

task createSourceDirs(description : 'Create empty source directories for all defined sourceSets') << {
    sourceSets*.allSource.srcDirs.flatten().each { File sourceDirectory ->
        if (!sourceDirectory.exists()) {
            println "Making $sourceDirectory"
            sourceDirectory.mkdirs()
        }
    }
}

idea {
    project {
        jdkName = '1.6'
    }
}


task syncWithRepo(dependsOn: 'classes', type: JavaExec) {
    main = 'com.entagen.jenkins.Main'
    classpath = sourceSets.main.runtimeClasspath
    // pass through specified system properties to the call to main
    ['help','mavenCmd','userProfile','emailId','test','jobPrefix','jenkinsUrl', 'jenkinsUser', 'jenkinsPassword', 'gitUrl', 'templateJobPrefix', 'templateBranchName', 'branchNameRegex', 'nestedView', 'printConfig', 'dryRun', 'startOnCreate', 'noViews', 'noDelete'].each {
        if (System.getProperty(it)) systemProperty it, System.getProperty(it)
    }

}

task wrapper(type: Wrapper) {
    gradleVersion = '1.0'
}






