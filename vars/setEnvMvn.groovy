#!/usr/bin/env groovy


/*
 *  Set global CI environment vars for Maven projects
 */

def call() {

  def foliociLib = new org.folio.foliociCommands()

  // static vars
  env.okapiUrl = 'http://folio-snapshot-stable.aws.indexdata.com:9130'
  env.folioRegistry = 'http://folio-registry.aws.indexdata.com'

  echo "Okapi URL: ${env.okapiUrl}"
  echo "FOLIO Registry: ${env.folioRegistry}"

  def mvn_artifact = readMavenPom().getArtifactId()
  def mvn_version =  readMavenPom().getVersion()
  env.name = mvn_artifact
  env.bareVersion = mvn_version
  
  // if release
  if ( foliociLib.isRelease() ) {
    // make sure git tag and maven version match
    if ( foliociLib.tagMatch(mvn_version) ) {
      env.version = mvn_version
      env.isRelease = true
      env.dockerRepo = 'folioorg'
    } 
    else {
      error('Git release tag and Maven version mismatch')
    } 
  }
  // else snapshot
  else {
    env.version = "${mvn_version}.${env.BUILD_NUMBER}"
    env.snapshot = true
    env.dockerRepo = 'folioci'
  } 

  // project name is the GitHub repo name
  env.projectName = foliociLib.getProjName()

  //git commit sha1
  env.gitCommit = foliociLib.gitCommit()
  env.projUrl = foliociLib.getProjUrl()

  echo "Maven Project Name: $env.name"
  echo "Maven Project Version: $env.version"
  echo "Git Project Name: $env.projectName"
  echo "Git Project Url: $env.projUrl"
  echo "Git Commit SHA1: $env.gitCommit"

}
