#!/usr/bin/env groovy

/*
 * Build stripes platform. 
 */


def call(String okapiUrl, String tenant) {

  def foliociLib = new org.folio.foliociCommands()

  // remove existing yarn.lock
  sh 'rm -f yarn.lock'

  sh 'yarn install'

  // generate platform mod descriptors
  // foliociLib.genStripesModDescriptors("${env.WORKSPACE}/artifacts/md")
  sh 'yarn build-module-descriptors'

  // build webpack with stripes-cli. See STCLI-66 re: PREFIX env
  sh "yarn build --okapi $okapiUrl --tenant $tenant ./bundle" 

  // generate tenant stripes module list
  writeFile file: 'md2install.sh', text: libraryResource('org/folio/md2install.sh')
  sh 'chmod +x md2install.sh'
  sh './md2install.sh --outputfile stripes-install.json ./ModuleDescriptors' 
  sh 'rm -f md2install.sh'

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
