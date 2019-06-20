/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.appengine.gcsClient

import com.netflix.spinnaker.clouddriver.appengine.AppengineJobExecutor
import com.netflix.spinnaker.clouddriver.appengine.artifacts.GcsStorageService
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineRepositoryClient
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactUtils
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

@CompileStatic
@Slf4j
@TupleConstructor
class AppengineGcsRepositoryClient implements AppengineRepositoryClient {
  String repositoryUrl
  String targetDirectoryPath
  String applicationDirectoryRoot
  GcsStorageService storage
  AppengineJobExecutor jobExecutor

  void initializeLocalDirectory() {
    downloadFiles()
  }

  void updateLocalDirectoryWithVersion(String version) {
    downloadFiles()
  }

  void downloadFiles() {
    def gsPrefix = "gs://"
    if (!repositoryUrl.startsWith(gsPrefix)) {
      throw new IllegalArgumentException("Repository is not a GCS bucket: " + repositoryUrl)
    }

    def dest = applicationDirectoryRoot ? targetDirectoryPath + File.separator + applicationDirectoryRoot : targetDirectoryPath

    def fullPath = repositoryUrl.substring(gsPrefix.length())
    if (applicationDirectoryRoot) {
      fullPath += "/${applicationDirectoryRoot}"
    }
    def slash = fullPath.indexOf("/")
    def bucketName = fullPath.substring(0, slash)
    def bucketPath = fullPath.substring(slash + 1)
    Long version = null

    def versionSeparator = bucketPath.indexOf("#")
    if (versionSeparator >= 0) {
      String versionString = bucketPath.substring(versionSeparator + 1)
      if (!versionString.isEmpty()) {
        version = Long.parseLong(versionString)
      }
      bucketPath = bucketPath.substring(0, versionSeparator)
    }

    // Start with a clean directory for each deployment.
    File targetDirectory = new File(targetDirectoryPath)
    if (targetDirectory.exists() && targetDirectory.isDirectory()) {
      FileUtils.forceDelete(targetDirectory)
    } else if (targetDirectory.exists() && targetDirectory.isFile()) {
      log.error("GAE staging directory resolved to a file: ${}, failing...")
      throw new IllegalArgumentException("GAE staging directory resolved to a file: ${}, failing...")
    }

    if (bucketPath.endsWith(".tar")) {
      InputStream tas = storage.openObjectStream(bucketName, bucketPath, version)

      // NOTE: We write the tar file out to an intermediate temp file because the tar input stream
      // directly from openObjectStream() closes unexpectedly when accessed from untarStreamToPath()
      // for some reason.
      File tempFile = File.createTempFile("app", "tar")
      FileOutputStream fos = new FileOutputStream(tempFile)
      IOUtils.copy(tas, fos)
      tas.close()
      fos.close()

      ArtifactUtils.untarStreamToPath(new FileInputStream(tempFile), dest)
      tempFile.delete()
    } else {
      storage.visitObjects(bucketName, bucketPath, { obj -> storage.downloadStorageObject(obj, dest) })
    }
  }
}
