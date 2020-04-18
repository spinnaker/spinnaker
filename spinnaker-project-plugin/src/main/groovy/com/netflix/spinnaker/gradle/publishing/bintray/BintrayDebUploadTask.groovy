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

  @InputFile
  Provider<RegularFile> archiveFile

  @TaskAction
  void uploadDeb() {
    def extension = project.extensions.getByType(BintrayPublishExtension)
    def url = publishUri.get().toURL()
    def file = archiveFile.get().asFile
    def contentLength = file.size()
    project.logger.info("Uploading $file to $url")
    HttpURLConnection con = (HttpURLConnection) url.openConnection()
    con.doOutput = true
    con.requestMethod = 'PUT'
    con.addRequestProperty('Authorization', extension.basicAuthHeader())
    con.setRequestProperty('Content-Type', 'application/octet-stream')
    con.setRequestProperty('Content-Length', "$contentLength")
    con.getOutputStream().withCloseable { OutputStream os ->
      file.newInputStream().withCloseable { InputStream is ->
        byte[] buf = new byte[16 * 1024]
        int bytesRead
        while ((bytesRead = is.read(buf)) != -1) {
          os.write(buf, 0, bytesRead)
        }
        os.flush()
        project.logger.info("upload complete")
      }
    }
    project.logger.info("Waiting for HTTP response")
    int httpStatus = con.responseCode
    project.logger.info("Upload finished with status $httpStatus: ${con.responseMessage}")
    (httpStatus >= 400 ?
      con.getErrorStream() :
      con.getInputStream()).withCloseable { InputStream is ->
      project.logger.debug("Upload response:\n$is.text")
    }
    con.disconnect()
  }
}
