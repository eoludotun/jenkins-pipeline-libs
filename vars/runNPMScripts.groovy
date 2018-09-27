#!/usr/bin/env groovy


/*
 * Run Node/NPM-based optional scripts 
 * 
 * Example:
 *
 * buildNPM { 
 *   runScripts ['script name':'script args']
 * }
 * 
 */

def call(String scriptName, String scriptArgs) {

  def XVFB = 'xvfb-run --server-args="-screen 0 1024x768x24"'
  def status
  def message

  stage('Run yarn $scriptName') {
    withEnv([ 
      'CHROME_BIN=/usr/bin/google-chrome-stable',
      'FIREFOX_BIN=/usr/bin/firefox',
      'DEBIAN_FRONTEND=noninteractive'
    ]) { 

      // disabled since we build new build images every week. 
      // get latest versions for browsers
      // sh 'sudo apt-get -q update'
      // sh 'sudo apt-get -y --no-install-recommends install google-chrome-stable'
      // sh 'sudo apt-get -y --no-install-recommends install firefox'

      // display available browsers/version
      sh "$CHROME_BIN --version"
      sh "$FIREFOX_BIN --version"

      if (runScripts.size() >= 1) { 
        for (script in runScripts) {
          scriptStatus = sh(returnStatus:true, script: "$XVFB yarn ${script.key} ${script.value}")
          if (scriptStatus != 0) { 
            errorMessage = "Test errors found for ${script.key}. See ${env.BUILD_URL}" 
            if (env.CHANGE_ID) {
              // Requires https://github.com/jenkinsci/pipeline-github-plugin
              @NonCPS
              comment = pullRequest.comment(errorMessage)
            }
            junit allowEmptyResults: true, testResults: 'artifacts/runTest/*.xml'
            error(errorMessage)
          }
        }
        // publish junit tests if available
        junit allowEmptyResults: true, testResults: 'artifacts/runTest/*.xml'
      }
      else {
        echo "No scripts to run."
      }
    } 
  } // end stage 
}
