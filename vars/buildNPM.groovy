#!/usr/bin/groovy


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  node('folio-jenkins-slave-docker') {

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

         echo "Checked out $env.BRANCH_NAME"
      }

      stage('Prep') {
        // right now, all builds are snapshots
        def Boolean snapshot = true

        if (snapshot == true) {
          def folioci_npmver = libraryResource('org/folio/folioci_npmver.sh')
          writeFile file: 'folioci_npmver.sh', text: folioci_npmver
          sh 'chmod +x folioci_npmver.sh'
          sh 'npm version `./folioci_npmver.sh`'
        }

        def json = readJSON(file: 'package.json')
        def name = json.name.replaceAll(~/\//, "_")  
        name = name.replaceAll(~/@/, "")  
        env.name = name
        env.version = json.version
        echo "Package Name: $env.name"
        echo "Package Version: $env.version"

        // project name is different from mod name specified in package.json
        def proj_name = sh(returnStdout: true, script: 'git config remote.origin.url | awk -F \'/\' \'{print $5}\' | sed -e \'s/\\.git//\'').trim()
        env.project_name = proj_name
        echo "$env.project_name"
      }

      stage('SonarQube Scan') {
        withSonarQubeEnv('SonarCloud') {
          echo "Performing SonarQube scan" 
          def scannerHome = tool 'SonarQube Scanner'
          if (env.BRANCH_NAME == 'master') {
            sh """
            ${scannerHome}/bin/sonar-scanner \
                            -Dsonar.projectKey=folio-org:${env.project_name} \
                            -Dsonar.projectName=${env.project_name} \
                            -Dsonar.projectVersion=${env.version} \
                            -Dsonar.sources=. \
                            -Dsonar.organization=folio-org 
            """
          }
          else { 
            // need to add some github stuff here 
            sh """
            ${scannerHome}/bin/sonar-scanner \
                            -Dsonar.projectKey=folio-org:${env.project_name} \
                            -Dsonar.projectName=${env.project_name} \
                            -Dsonar.projectVersion=${env.version} \
                            -Dsonar.sources=. \
                            -Dsonar.organization=folio-org \
                            -Dsonar.analysis.mode=preview \
            """
          }
        }
      }
      stage('NPM Build') {
        // We should probably use the --production flag here for releases
        sh 'npm install' 
      }

      if ( env.BRANCH_NAME == 'master' ) {
        stage('NPM Deploy') {
          echo "Deploying NPM packages to Nexus repository"
          sh 'npm publish'
        }

        if (config.publishModDescriptor ==~ /(?i)(Y|YES|T|TRUE)/) {
          stage('Publish Module Descriptor') {
            echo "Publishing Module Descriptor to FOLIO registry"
            sh 'git clone https://github.com/folio-org/stripes-core'
            sh 'stripes-core/util/package2md.js --strict package.json > ModuleDescriptor.json'
            def modDescriptor = 'ModuleDescriptor.json'

            postModuleDescriptor(modDescriptor,env.name,env.version) 
          }
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
  } //end node
    
} 

