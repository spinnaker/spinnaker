package com.netflix.spinnaker.gradle.publishing.bintray

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

class BintrayDebUploadTask extends DefaultTask {

  @Input
  Provider<String> publishUri

  @Input
  String bintrayAuthHeader

  @InputFile
  Provider<RegularFile> archiveFile

  @TaskAction
  void uploadDeb() {
    def url = publishUri.get()
    def http = new HttpUtil(url, bintrayAuthHeader, 'PUT')
    def file = archiveFile.get().asFile
    project.logger.info("Uploading $file to $url")
    http.uploadFile(file)
    project.logger.info("Waiting for HTTP response")
    def response = http.response
    project.logger.info("Upload finished with status $response.responseCode: $response.statusLine")
    project.logger.info("Upload response:\n$response.responseBody")
    response.throwIfFailed("upload deb $file.name")
  }
}
