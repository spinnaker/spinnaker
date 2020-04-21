package com.netflix.spinnaker.gradle.publishing.bintray

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class BintrayCreateVersionTask extends DefaultTask {
  @Input
  Provider<String> createVersionUri

  @Input
  Provider<String> packageName

  @Input
  String bintrayAuthHeader

  @TaskAction
  void createVersion() {
    def url = createVersionUri.get()
    def http = new HttpUtil(url, bintrayAuthHeader, 'POST')
    String createVersion = """\
    {
      "name": "${project.version}",
      "desc": "${packageName.get()} ${project.version}"
    }
    """.stripIndent()
    project.logger.info("POSTing create version request to $url")
    http.uploadJson(createVersion)
    project.logger.info("Waiting for HTTP response")
    def response = http.response
    project.logger.info("Create Version request finished with status $response.responseCode: $response.statusLine")
    project.logger.debug("Create Version response:\n$response.responseBody")
  }
}
