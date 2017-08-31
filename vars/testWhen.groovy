#!/usr/bin/groovy


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  node {

    try {
      stage('One') {
        echo "Branch is: $env.BRANCH_NAME"
        echo "Perform Stage One"
        sh '/bin/false'
      }

      stage('Two') {
        echo "Perform Stage Two"
      }
   
      //if ( env.BRANCH_NAME == 'master' ) {    
      if ( env.BRANCH_NAME == 'malc-test' ) {    
        echo "config.doDocker is: $config.doDocker" 

        if ( config.doDocker ==~ /(?i)(Y|YES|T|TRUE)/ ) {
          stage('Docker') {
            echo "Building Docker" 
          }
          stage('Docker Publish') {
            echo "Publishing Docker"
          }
        } 
        if (config.publishModDescriptor ==~ /(?i)(Y|YES|T|TRUE)/) {
          stage('Publish Module Descriptor') {
            echo "Publishing Module Descriptor"
          }
        }
        if (config.publishAPI ==~ /(?i)(Y|YES|T|TRUE)/) {
          stage('Publish API Docs') {
          echo "Publishing API docs"
          }
        }
      } 
    } // end try
    catch (Exception err) {
      currentBuild.result = 'FAILED'
      println(err.toString());
      println(err.getMessage());
      println(err.getStackTrace());
      echo "Build Result: $currentBuild.result"
      throw err
    
    }
    finally {
      echo "Send some notifications"
      echo "Do some cleanup"
    }
  } //end node
    
} 

