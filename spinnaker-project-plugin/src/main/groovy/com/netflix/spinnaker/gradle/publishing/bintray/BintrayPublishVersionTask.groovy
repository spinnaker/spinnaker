package com.netflix.spinnaker.gradle.publishing.bintray

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class BintrayPublishVersionTask extends DefaultTask {
  @Input
  Provider<String> publishUri

  @Input
  Provider<Integer> publishWaitForSecs

  @Input
  String bintrayAuthHeader

  @TaskAction
  void publishVersion() {
    def url = publishUri.get()
    def http = new HttpUtil(url, bintrayAuthHeader, 'POST')
    String publishVersion = """\
    {
      "publish_wait_for_secs": "${publishWaitForSecs.get()}"
    }
    """.stripIndent()
    project.logger.info("POSTing version publish request to $url")
    http.uploadJson(publishVersion)
    project.logger.info("Waiting for HTTP response")
    def response = http.response
    project.logger.info("Version publish finished with status $response.responseCode: $response.statusLine")
    project.logger.info("Version publish response:\n$response.responseBody")
    response.throwIfFailed("publish version")
  }
}
