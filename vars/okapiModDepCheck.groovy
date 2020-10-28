#!/usr/bin/env groovy

/*
 * Test module dependency resolution
 */

def call(String mdFile) {
 
  def okapiPull = "{ \"urls\" : [ \"${env.folioRegistry}\" ]}"
  def mdUrl 
  def md = readFile(mdFile)
  

  docker.withRegistry('https://index.docker.io/v1/', 'dockerhub-ci-pull-account') {
    docker.image('folioorg/okapi:latest').withRun('', 'dev') { container ->
      def okapiIp = sh(returnStdout:true, script: "docker inspect --format='{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' ${container.id}").trim()

      if (env.isRelease) {
        mdUrl = "http://${okapiIp}:9130/_/proxy/modules?preRelease=false&npmSnapshot=false"
      }
      else {
        mdUrl = "http://${okapiIp}:9130/_/proxy/modules"
      }
    
      // make sure okapi is fully started
      sleep 5

      // pull all MDs
      httpRequest acceptType: 'APPLICATION_JSON_UTF8', 
                  contentType: 'APPLICATION_JSON_UTF8', 
                  consoleLogResponseBody: false,
                  httpMode: 'POST',
                  requestBody: okapiPull, 
                  url: "http://${okapiIp}:9130/_/proxy/pull/modules"

      // POST our MD
      httpRequest acceptType: 'APPLICATION_JSON_UTF8', 
                  contentType: 'APPLICATION_JSON_UTF8', 
                  consoleLogResponseBody: true,
                  httpMode: 'POST',
                  requestBody: md, 
                  url: mdUrl

    } // destroy okapi container
  }
}
