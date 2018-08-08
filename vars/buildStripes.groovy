#!/usr/bin/env groovy

/*
 * Build stripes bundle.  Use cases:  'platform' or 'app' only.  PR or non-PR.
 */


def call(String okapiUrl, String tenant, String stripesPlatform = null) {

  stage('Build Stripes') {

    if (stripesPlatform != 'none') {
      dir("${env.WORKSPACE}") {
        sh "git clone https://github.com/folio-org/${stripesPlatform}"
      }

      dir("${env.WORKSPACE}/${stripesPlatform}") {
        if (env.CHANGE_ID) { 
          // remove node_modules directory in proj dir created by previous 'yarn install'. See FOLIO-1338
          sh 'rm -rf ../project/node_modules'

          // remove yarn.lock if it exists 
          sh 'rm -f yarn.lock'

          // Disable use of yarn.lock for now
          /* 
          * // grab yarn lock from folio-snapshot-stable
          * sh 'wget -O yarn.lock http://folio-snapshot-stable.aws.indexdata.com/yarn.lock'
          * 
          * // check to see we actually have a real yarn.lock
          * def isYarnLock = sh (script: 'grep "yarn lockfile" yarn.lock > /dev/null', 
          *                     returnStatus: true)
          * 
          * if (isYarnLock != 0) { 
          *   error('unable to fetch yarn.lock for folio-snapshot-stable')
          * }
          */

          // substitute PR commit for package
          sh "yarn add file:../project"
          sh "yarn upgrade $env.npmName"
        }
        else {
          // substitute git commit sha1 for package
          sh 'yarn install'
        }

        // generate mod descriptors with dependencies
        if (stripesPlatform == 'folio-testing-platform') { 
          sh 'yarn postinstall --strict'
        }
        else {
          sh 'yarn generate-mod-descriptors --strict'
        }

        // build webpack with stripes-cli. See STCLI-66 re: PREFIX env
        sh "yarn build --okapi $okapiUrl --tenant $tenant stripes.config.js ./bundle" 

        // start simple webserver to serve webpack
        withEnv(['JENKINS_NODE_COOKIE=dontkill']) {
          sh 'yarn stripes serve --existing-build &'
        }

        // publish generated yarn.lock for possible debugging
        sh 'mkdir -p ci'
        sh 'cp yarn.lock ci/yarnLock.html'
        publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
                    keepAll: true, reportDir: 'ci',
                    reportFiles: 'yarnLock.html',
                    reportName: "YarnLock",
                    reportTitles: "YarnLock"])

        // publish stripes bundle for debugging
        archiveWebpack('./bundle')
      }
    }
    else { 
      // build in stripes-cli 'app' mode
      sh 'PREFIX=/usr/local/share/.config/yarn stripes build --output=./bundle'
      sh "PREFIX=/usr/local/share/.config/yarn stripes mod descriptor --full --strict > ${env.projectName}.json"
    }

  } // end stage
} 
