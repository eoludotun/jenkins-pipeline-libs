#!/usr/bin/env groovy

/*
 * Main build script for NPM-based FOLIO projects
 *
 * Configurable parameters: 
 *
 * doDocker:  Build, test, and publish Docker image via 'buildDocker' (Default: 'no')
 * runLint: Run ESLint via 'yarn lint' (Default: 'no')
 * runTest: Run unit tests via 'yarn test' (Default: 'no')
 * runTestOptions:  Extra opts to pass to 'yarn test'
 * runRegression: Run UI regression module tests for PRs - 'yes' or 'no' (Default: 'no') 
 * regressionDebugMode:  Enable extra debug logging in regression tests (Default: false)
 * npmDeploy: Publish NPM artifacts to NPM repository (Default: 'yes')
 * publishModDescriptor:  POST generated module descriptor to FOLIO registry (Default: 'no')
 * modDescriptor: path to standalone Module Descriptor file (Optional)
 * publishApi: Publish API/RAML documentation.  (Default: 'no')
 * buildNode: label of jenkin's slave build node to use
*/


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def foliociLib = new org.folio.foliociCommands()
  
  // default is to deploy to npm repo when branch is master
  def npmDeploy = config.npmDeploy ?: 'yes'

  // default is don't run regression tests for PRs
  def runRegression = config.runRegression ?: 'no'

  // enable debugging logging on regression tests 
  def regressionDebugMode = config.regressionDebugMode ?: false

  // default runTestOptions
  def runTestOptions = config.runTestOptions ?: ''

  // default mod descriptor
  def modDescriptor = config.modDescriptor ?: ''

  // default Stripes platform.  '
  // env.stripesPlatform = config.stripesPlatform ?: ''

  // use the smaller nodejs build node since most 
  // Nodejs builds are Stripes.
  def buildNode = config.buildNode ?: 'jenkins-slave-all'

  // right now, all builds are snapshots unless they are PRs
  if (!env.CHANGE_ID) {
    env.snapshot = true
  }
  
  env.dockerRepo = 'folioci'

  properties([buildDiscarder(logRotator(artifactDaysToKeepStr: '', 
                                          artifactNumToKeepStr: '30', 
                                          daysToKeepStr: '', 
                                          numToKeepStr: '30'))]) 
 
  
  node(buildNode) {
    timeout(60) { 

      try {
        stage('Checkout') {
          deleteDir()
          currentBuild.displayName = "#${env.BUILD_NUMBER}-${env.JOB_BASE_NAME}"
          sendNotifications 'STARTED'

          checkout([
                 $class: 'GitSCM',
                 branches: scm.branches,
                 extensions: scm.extensions + [[$class: 'RelativeTargetDirectory',
                                                       relativeTargetDir: 'project'],
                                              [$class: 'SubmoduleOption',
                                                       disableSubmodules: false,
                                                       parentCredentials: false,
                                                       recursiveSubmodules: true,
                                                       reference: '',
                                                       trackingSubmodules: false]],
                 userRemoteConfigs: scm.userRemoteConfigs
           ])

           echo "Checked out branch:  $env.BRANCH_NAME"
        }

        dir("${env.WORKSPACE}/project") {
          stage('Prep') {

            if (env.snapshot) {
              foliociLib.npmSnapshotVersion()
            }
 
            if (env.CHANGE_ID) {
              foliociLib.npmPrVersion()
            } 
          

            // the actual NPM package name as defined in package.json
            env.npmName = foliociLib.npmName('package.json')

            // simpleName is similar to npmName except make name okapi compliant
            def Map simpleNameVerMap = foliociLib.npmSimpleNameVersion('package.json')          
            simpleNameVerMap.each { key, value ->
              env.simpleName = key
              env.version = value
            }
            // "short" name e.g. 'folio_users' -> 'users'
            env.npmShortName = foliociLib.getNpmShortName(env.simpleName)

            // project name is the GitHub repo name and is typically
            // different from mod name specified in package.json
            env.projectName = foliociLib.getProjName()

            //git commit sha1
            env.gitCommit = foliociLib.getCommitSha()
            env.projUrl = foliociLib.getProjUrl()

            echo "Package Name: $env.npmName"
            echo "Package FOLIO Name: $env.simpleName"
            echo "Package Short Name: $env.npmShortName"
            echo "Package Version: $env.version"
            echo "Project Name: $env.projectName"
            echo "Git SHA1: $env.gitCommit"
            echo "Project Url: $env.projUrl"
          }  
 
          withCredentials([string(credentialsId: 'jenkins-npm-folioci',variable: 'NPM_TOKEN')]) {
            withNPM(npmrcConfig: 'jenkins-npm-folioci') {
              stage('NPM Install') {
                sh 'yarn install' 
             
              }

              if (config.runLint ==~ /(?i)(Y|YES|T|TRUE)/) {
                runLintNPM()
              } 

              if (config.runTest ==~ /(?i)(Y|YES|T|TRUE)/) {
                runTestNPM(runTestOptions)
              }

              stage('Generate Module Descriptor') { 
                // really meant to cover non-Stripes module cases. e.g mod-graphql
                if (modDescriptor) {       
                  env.name = env.projectName
                  if (env.snapshot) {
                    // update the version to the snapshot version
                    echo "Update Module Descriptor version to snapshot version"
                    foliociLib.updateModDescriptorId(modDescriptor)
                  }
                }
                // Stripe modules
                else {
                  echo "Generating Stripes module descriptor from package.json"
                  sh 'mkdir -p ${env.WORKSPACE}/artifacts/md'
                  sh "stripes mod descriptor --full --strict | jq '.[]' " +
                     "> ${env.WORKSPACE}/artifacts/md/${env.simpleName}.json"
                  modDescriptor = "${env.WORKSPACE}/artifacts/md/${env.simpleName}.json"
                }
              } 

              if ( env.BRANCH_NAME == 'master' ) {
                if (npmDeploy ==~ /(?i)(Y|YES|T|TRUE)/) {
                  stage('NPM Publish') {
                    // npm is more flexible than yarn for this stage. 
                    echo "Deploying NPM packages to Nexus repository"
                    sh 'npm publish -f'
                  }
                }
              }

            }  // end withNPM
            // remove .npmrc put in directory by withNPM
            sh 'rm -f .npmrc'
          }  // end WithCred    

          if (config.doDocker) {
            stage('Docker Build') {
              // use env.projectName as name of docker artifact
              env.name = env.projectName
              echo "Building Docker image for $env.name:$env.version" 
              config.doDocker.delegate = this
              config.doDocker.resolveStrategy = Closure.DELEGATE_FIRST
              config.doDocker.call()
            }
          } 

          if ( env.BRANCH_NAME == 'master' ) {
            if (config.publishModDescriptor ==~ /(?i)(Y|YES|T|TRUE)/) {
              // We assume that MDs are included in package.json
              stage('Publish Module Descriptor') {
                echo "Publishing Module Descriptor to FOLIO registry"
                postModuleDescriptor(modDescriptor) 
              }
            }

            if (config.publishAPI ==~ /(?i)(Y|YES|T|TRUE)/) {
              stage('Publish API Docs') {
                echo "Publishing API docs"
                sh "python3 /usr/local/bin/generate_api_docs.py -r $env.project_name -l info -o folio-api-docs"
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                     accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                     credentialsId: 'jenkins-aws',
                     secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                  sh 'aws s3 sync folio-api-docs s3://foliodocs/api'
                }
              }
            }
          } 
        } // end dir

        if (env.CHANGE_ID) {

          // ensure tenant id is unique
          // def tenant = "${env.BRANCH_NAME}_${env.BUILD_NUMBER}"
          def tenant = "pr_${env.CHANGE_ID}_${env.BUILD_NUMBER}"
          tenant = foliociLib.replaceHyphen(tenant)
          def okapiUrl = 'http://folio-snapshot-stable.aws.indexdata.com:9130'


          if (runRegression ==~ /(?i)(Y|YES|T|TRUE)/) { 
            def tenantStatus = deployTenant("$okapiUrl","$tenant") 

            if (tenantStatus != 0) {
              echo "Problem deploying tenant. Skipping UI Regression testing."
            }
            else { 
              dir("${WORKSPACE}/project") { 
                echo "Running UI Integration tests"
                runIntegrationTests(regressionDebugMode,okapiUrl,tenant,"${tenant}_admin",'admin')
              }
            }
          }
        }
      }  // end try
      catch (Exception err) {
        currentBuild.result = 'FAILED'
        println(err.getMessage());
        echo "Build Result: $currentBuild.result"
        throw err
      }
      finally {
        sendNotifications currentBuild.result
      }
    } // end timeout
  } // end node
    
} 

