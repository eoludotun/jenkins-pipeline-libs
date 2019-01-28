#!/usr/bin/env groovy

/*
 * Build stripes platform. 
 */


def call(String okapiUrl, String tenant) {

  def foliociLib = new org.folio.foliociCommands()

  sh 'yarn install'

  // generate platform mod descriptors
  // foliociLib.genStripesModDescriptors("${env.WORKSPACE}/artifacts/md")
  sh 'yarn build-module-descriptors'

  // build webpack with stripes-cli. See STCLI-66 re: PREFIX env
  sh "yarn build --okapi $okapiUrl --tenant $tenant ./bundle" 

  // publish generated yarn.lock for possible debugging
  sh 'mkdir -p ci'
  sh 'cp yarn.lock ci/yarnLock.html'
  publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
               keepAll: true, reportDir: 'ci',
               reportFiles: 'yarnLock.html',
               reportName: "YarnLock",
               reportTitles: "YarnLock"])

  // publish stripes bundle for debugging
  // archiveWebpack('./bundle')
  // end stage
} 
