#!/usr/bin/groovy

/*
 * Main build script for Maven-based FOLIO projects
 *
 * Configurable parameters: 
 *
 * doDocker:  Build, test, and publish Docker image via 'buildJavaDocker' (Default: 'no'/false)
 * mvnDeploy: Deploy built artifacts to Maven repository (Default: 'no'/false)
 * publishModDescriptor:  POST generated module descriptor to FOLIO registry (Default: 'no'/false)
 * publishAPI: Publish API RAML documentation.  (Default: 'no'/false)
 * runLintRamlCop: Run 'raml-cop' on back-end modules that have declared RAML in api.yml (Default: 'no'/false)
*/
 


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def foliociLib = new org.folio.foliociCommands()

  // Lint RAML for RAMLCop.  default is false
  def runLintRamlCop = config.runLintRamlCop ?: false
  if (runLintRamlCop ==~ /(?i)(Y|YES|T|TRUE)/) { runLintRamlCop = true } 
  if (runLintRamlCop ==~ /(?i)(N|NO|F|FALSE)/) { runLintRamlCop = false} 

  // publish maven artifacts to Maven repo.  Default is false
  def mvnDeploy = config.mvnDeploy ? false
  if (mvnDeploy ==~ /(?i)(Y|YES|T|TRUE)/) { mvnDeploy = true }
  if (mvnDeploy ==~ /(?i)(N|NO|F|FALSE/) { mvnDeploy = false }

  // publish mod descriptor to folio-registry. Default is false
  def publishModDescriptor = config.publishModDescriptor ?: false
  if (publishModDescriptor ==~ /(?i)(Y|YES|T|TRUE)/) { publishModDescriptor = true }
  if (publishModDescriptor ==~ /(?i)(N|NO|F|FALSE)/) { publishModDescriptor = false }

  // publish API documentation to foliodocs. Default is false
  def publishAPI = config.publishAPI ?: false
  if (publishAPI ==~ /(?i)(Y|YES|T|TRUE)/) { publishAPI = true }
  if (publishAPI ==~ /(?i)(N|NO|F|FALSE)/) { publishAPI = false }



  def buildNode = config.buildNode ?: 'jenkins-slave-all'

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
                 extensions: scm.extensions + [[$class: 'SubmoduleOption',
                                                       disableSubmodules: false,
                                                       parentCredentials: false,
                                                       recursiveSubmodules: true,
                                                       reference: '',
                                                       trackingSubmodules: false]],
                 userRemoteConfigs: scm.userRemoteConfigs
          ])

          echo "Checked out branch: $env.BRANCH_NAME"
        }

        stage('Set Environment') {
          setEnvMvn()
        }

        if (runLintRamlCop) {
          stage('Lint raml-cop') {
            runLintRamlCop()
          }
        }

        stage('Maven Build') {
          echo "Building Maven artifact: ${env.name} Version: ${env.version}"
          withMaven(jdk: 'openjdk-8-jenkins-slave-all',  
                    maven: 'maven3-jenkins-slave-all',  
                    mavenSettingsConfig: 'folioci-maven-settings') {
    
            // Check to see if we have snapshot deps in release
            if (env.isRelease) {
              def snapshotDeps = checkMvnReleaseDeps() 
              if (snapshotDeps) { 
                echo "$snapshotDeps"
                error('Snapshot dependencies found in release')
              }
            }
            sh 'mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent install'
          }
        }

        // Run Sonarqube
        stage('SonarQube Analysis') {
          sonarqubeMvn() 
        }
      
        // Docker stuff
        if (config.doDocker) {
          stage('Docker Build') {
            echo "Building Docker image for $env.name:$env.version" 
            config.doDocker.delegate = this
            config.doDocker.resolveStrategy = Closure.DELEGATE_FIRST
	    config.doDocker.call()
          }
        } 

        // master branch or tagged releases
        if (( env.BRANCH_NAME == 'master' ) || ( env.isRelease )) {

          if (mvnDeploy) {
            stage('Maven Deploy') {
              echo "Deploying artifacts to Maven repository"
              withMaven(jdk: 'openjdk-8-jenkins-slave-all', 
                      maven: 'maven3-jenkins-slave-all', 
                      mavenSettingsConfig: 'folioci-maven-settings') {
                sh 'mvn -DskipTests deploy'
              }
            }
          }
          if (publishModDescriptor) {
            stage('Publish Module Descriptor') {
              echo "Publishing Module Descriptor to FOLIO registry"
              def modDescriptor = 'target/ModuleDescriptor.json'
              foliociLib.updateModDescriptor(modDescriptor)
              postModuleDescriptor(modDescriptor) 
            }
          }
          if (publishAPI) {
            stage('Publish API Docs') {
              echo "Publishing API docs"
              sh "python3 /usr/local/bin/generate_api_docs.py -r $env.projectName -l info -o folio-api-docs"
              withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', 
                   accessKeyVariable: 'AWS_ACCESS_KEY_ID', 
                   credentialsId: 'jenkins-aws', 
                   secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                sh 'aws s3 sync folio-api-docs s3://foliodocs/api'
              }
            }
          }
        }

        if (runLintRamlCop) {
          stage('Lint raml schema') {
            runLintRamlSchema()
          }
        }
      } // end try
      catch (Exception err) {
        currentBuild.result = 'FAILED'
        println(err.getMessage());
        echo "Build Result: $currentBuild.result"
        throw err
      }
      finally {
        sendNotifications currentBuild.result
      }
    } //end timeout
  } // end node
} 

