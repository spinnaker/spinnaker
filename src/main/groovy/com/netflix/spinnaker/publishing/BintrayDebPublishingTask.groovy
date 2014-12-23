/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.publishing

import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.BintrayHttpClientFactory
import groovyx.net.http.HTTPBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction


import static groovyx.net.http.ContentType.BINARY
import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.HEAD
import static groovyx.net.http.Method.POST
import static groovyx.net.http.Method.PUT

class BintrayDebPublishingTask extends DefaultTask {

  static final String GROUP = 'publishing'
  static final String DESCRIPTION = 'Publishes debian artifacts to bintray.com.'

  @Delegate BintrayExtension baseExtension

  String userOrg
  String repoName
  String packageName
  String version

  String distribution
  String component
  String architecture
  String packagePath

  File debFile
  boolean dryRun

  {
    group = GROUP
    description = DESCRIPTION
  }

  @TaskAction
  void bintrayUpload() {
    def http = BintrayHttpClientFactory.create(getApiUrl(), getUser(), getKey())

    def uploadUri = "/content/${getUserOrg()}/${getRepoName()}/${getPackageName()}/${getVersion()}/${getPackagePath()}"

    def matrixArgs = []
    if (getDistribution()) {
      matrixArgs << "deb_distribution=${getDistribution()}"
    }
    if (getComponent()) {
      matrixArgs << "deb_component=${getComponent()}"
    }
    if (getArchitecture()) {
      matrixArgs << "deb_architecture=${getArchitecture()}"
    }
    matrixArgs << "publish=1"

    uploadUri += ";${matrixArgs.join(';')}"

    checkAndCreatePackage http

    getDebFile().withInputStream { is ->
      is.metaClass.totalBytes = { getDebFile().size() }

      logger.info("Uploading to ${getApiUrl()}$uploadUri...")
      if (getDryRun()) {
        logger.info("(Dry run) Uploaded to '${getApiUrl()}$uploadUri'.")
        return
      }
      http.request(PUT) {
        uri.path = uploadUri
        requestContentType = BINARY
        body = is
        response.success = { resp ->
          logger.info("Uploaded to '${getApiUrl()}$uri.path'.")
        }
        response.failure = { resp ->
          throw new GradleException("Could not upload to '${getApiUrl()}$uri.path': $resp.statusLine")
        }
      }
    }
  }

  def checkAndCreatePackage(HTTPBuilder http) {
    def create
    http.request(HEAD) {
      uri.path = "/packages/${getUserOrg()}/${getRepoName()}/${getPackageName()}"
      response.success = { resp ->
        logger.debug("Package '${getPackageName()}' exists.")
      }
      response.'404' = { resp ->
        logger.info("Package '${getPackageName()}' does not exist. Attempting to creating it...")
        create = true
      }
    }
    if (create) {
      if (dryRun) {
        logger.info("(Dry run) Created pakage '${getPackageName()}'.")
        return
      }
      http.request(POST, JSON) {
        uri.path = "/packages/${getUserOrg()}/${getRepoName()}/${getPackageName()}"
        body = [name: getPackageName(),
                desc: getPkg().getDesc(),
                licenses: getPkg().getLicenses(),
                labels: getPkg().getLabels(),
                website_url: getPkg().getWebsiteUrl(),
                issue_tracker_url: getPkg().getIssueTrackerUrl(),
                vcs_url: getPkg().getVcsUrl(),
                public_download_numbers: getPkg().getPublicDownloadNumbers()]

        response.success = { resp ->
          logger.info("Created package '${getPackageName()}'.")
        }
        response.failure = { resp ->
          throw new GradleException("Could not create package '${getPackageName()}': $resp.statusLine")
        }
      }
    }
  }

}
