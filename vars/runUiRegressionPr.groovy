#!/usr/bin/env groovy


/*
 * Run UI Regression tests on PRs
 */

def call(String folioUser, String folioPassword, String folioUrl) {

  def status
  def testMessage
  def regressionReportUrl = "${env.BUILD_URL}UIRegressionTestReport/"
 
  stage('Run UI Regression Tests') {

    // clone ui-testing repo
    dir("$env.WORKSPACE") { 
      sh 'git clone https://github.com/folio-org/ui-testing'
    }

    dir ("${env.WORKSPACE}/ui-testing") { 

      sh "yarn link $env.npm_name"
      sh 'rm -f yarn.lock'
    
      withCredentials([string(credentialsId: 'jenkins-npm-folioci',variable: 'NPM_TOKEN')]) {
        withNPM(npmrcConfig: 'jenkins-npm-folioci') {
          sh 'yarn install' 
          sh '/usr/bin/Xvfb :2 &'
          sh 'sleep 1'

          env.FOLIO_UI_USERNAME = folioUser
          env.FOLIO_UI_PASSWORD = folioPassword
          env.FOLIO_UI_URL = folioUrl

          sh 'mkdir -p ci_reports'

          echo "Running UI Regression test against $folioUrl"
          //status = sh(script: "DEBUG=* DISPLAY=:2 yarn test >> ci_reports/rtest.html 2>&1", returnStatus:true)
          status = sh(script: "DISPLAY=:2 yarn test >> ci_reports/rtest.html 2>&1", returnStatus:true)
        }
      }
 
      // print test results to job console
      def testReport =  readFile('ci_reports/rtest.html')
      echo "$testReport"

      // publish results
      publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, 
                   keepAll: true, reportDir: 'ci_reports', 
                   reportFiles: 'rtest.html', 
                   reportName: 'UIRegressionTestReport', 
                   reportTitles: 'UIRegressionTestReport'])

      // publish generated yarn.lock
      sh 'cat yarn.lock >> ci_reports/uitest-yarnlock.html'
      publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
                   keepAll: true, reportDir: 'ci_reports',
                   reportFiles: 'uitest-yarnlock.html',
                   reportName: 'UITestingYarnLock',
                   reportTitles: 'UITestingYarnLock'])
    
      if (status != 0) { 
        testMessage = "UI Regression Tests FAILURES. Details at:  $regressionReportUrl"
      }
      else 
        testMessage = "All UI Regression Tests PASSED. Details at:  $regressionReportUrl" 
      }
     
      echo "$testMessage"

      if (env.CHANGE_ID) { 
        pullRequest.comment(testMessage)
      }

    } // end dir
  } // end stage
}
