#!/usr/bin/env groovy


/*
 * Send notifications based on build status string
 * TODO: Move to src/org/folio
 */

def call(String modDescriptor) {

  def folioRegistryUrl

   
  // if this is a release, verify dep resolution against releases only.
  if (env.isRelease) {
    folioRegistryUrl = "${folioRegistry}/_/proxy/modules?preRelease=false"
  }
  else {
    folioRegistryUrl = "${folioRegistry}/_/proxy/modules"
  }
    

  def request = readFile(modDescriptor)
  echo "Module Descriptor:"
  echo "$request"

  httpRequest acceptType: 'APPLICATION_JSON_UTF8', 
              contentType: 'APPLICATION_JSON_UTF8', 
              httpMode: 'POST', 
              consoleLogResponseBody: true,
              requestBody: request, 
              url: folioRegistryUrl
      
}
