#!/usr/bin/env groovy

/* 
 * Run the SonarQube scanner for JS-based projects
 * 
 */


def call(String lcovPath = 'artifacts/coverage') {
  withCredentials([[$class: 'StringBinding', 
                        credentialsId: '6b0ebf62-3a12-4e6b-b77e-c45817b5791b', 
                        variable: 'GITHUB_ACCESS_TOKEN']]) {
    withSonarQubeEnv('SonarCloud') {
      def scannerHome = tool 'SonarQube Scanner'
      def excludeFiles = '**/docs/**,**/node_modules/**,**/artifacts/**,**/ci/**,Jenkinsfile,**/LICENSE,**/*.css,**/*.md,**/*.json,**/tests/**/*-test.js,**/stories/*.js **/.stories.js'

      if (env.CHANGE_ID) {
        sh "${scannerHome}/bin/sonar-scanner " +
          "-Dsonar.projectKey=folio-org:${env.projectName} " +
          "-Dsonar.organization=folio-org " +
          "-Dsonar.sources=. " +
          "-Dsonar.language=js " +
          "-Dsonar.verbose=true " +
          "-Dsonar.exclusions=${excludeFiles} " +
          "-Dsonar.pullrequest.base=master " +
          "-Dsonar.pullrequest.key=${env.CHANGE_ID} " +
          "-Dsonar.pullrequest.branch=${env.BRANCH_NAME} " +
          "-Dsonar.pullrequest.provider=GitHub " + 
          "-Dsonar.pullrequest.github.repository=folio-org/${env.projectName} " + 
          "-Dsonar.pullrequest.github.endpoint=https://api.github.com"
      }
      else {  
        if (env.BRANCH_NAME == 'master') {
          sh "${scannerHome}/bin/sonar-scanner " +
            "-Dsonar.organization=folio-org " +
            "-Dsonar.projectKey=folio-org:${env.projectName} " +
            "-Dsonar.sources=. " +
            "-Dsonar.language=js " +
            "-Dsonar.exclusions=${excludeFiles} " +
            "-Dsonar.javascript.lcov.reportPaths=${lcovPath}/lcov.info" 
        }
      }

    } // end withSonarQubeenv
  } // end withCredentials
}

