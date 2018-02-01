/*
 * Copyright 2016 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.util

class BuildDetailExtractor {

  private final List<DetailExtractor> detailExtractors

  BuildDetailExtractor() {
    this.detailExtractors = [new DefaultDetailExtractor(), new LegacyJenkinsUrlDetailExtractor()]
  }

  public tryToExtractBuildDetails(Map buildInfo, Map request) {
    // The first strategy to succeed ends the loop. That is: the DefaultDetailExtractor is trying first
    // if it can not succeed the Legacy parser will be applied
    detailExtractors.any {
      it.tryToExtractBuildDetails(buildInfo, request)
    }
  }

  //Legacy Details extractor for Jenkins. It parses the url to fill the request build parameters
  @Deprecated
  private static class LegacyJenkinsUrlDetailExtractor implements DetailExtractor {

    boolean tryToExtractBuildDetails(Map buildInfo, Map request) {

      if (buildInfo == null || request == null) {
        return false
      }
      Map copyRequest = [:]
      def buildInfoUrlParts
      def buildInfoUrl = buildInfo.url
      if (buildInfoUrl) {
        buildInfoUrlParts = parseBuildInfoUrl(buildInfoUrl)
        if (buildInfoUrlParts?.size == 3) {
          copyRequest.put('buildInfoUrl', buildInfoUrl)
          copyRequest.put('buildHost', buildInfoUrlParts[0].toString())
          copyRequest.put('job', buildInfoUrlParts[1].toString())
          copyRequest.put('buildNumber', buildInfoUrlParts[2].toString())
          extractCommitHash(buildInfo, copyRequest)
          request.putAll(copyRequest)
          return true
        }
      }
      return false
    }

    // Naming-convention for buildInfo.url is $protocol://$buildHost/job/$job/$buildNumber/.
    // For example: http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/69/
    // Note that job names can contain slashes if using the Folders plugin.
    // For example: http://spinnaker.builds.test.netflix.net/job/folder1/job/job1/69/
    private parseBuildInfoUrl(String url) {
      List<String> urlParts = url?.tokenize("/")
      if (urlParts?.size >= 5) {
        def buildNumber = urlParts.pop()
        def job = urlParts[3..-1].join('/')

        def buildHost = "${urlParts[0]}//${urlParts[1]}/"

        return [buildHost, job, buildNumber]
      }
    }
  }

  //Default detail extractor. It expects to find url, name and number in the buildInfo
  private static class DefaultDetailExtractor implements DetailExtractor {

    boolean tryToExtractBuildDetails(Map buildInfo, Map request) {

      if (buildInfo == null || request == null) {
        return false
      }
      if (buildInfo.url && buildInfo.name && buildInfo.number) {
        Map copyRequest = [:]
        copyRequest.put('buildInfoUrl', buildInfo.url)
        copyRequest.put('job', buildInfo.name)
        copyRequest.put('buildNumber', buildInfo.number)
        extractBuildHost(buildInfo.url, copyRequest)
        extractCommitHash(buildInfo, copyRequest)
        request.putAll(copyRequest)
        return true
      }
      return false
    }


    private void extractBuildHost(String url, Map request) {
      List<String> urlParts = url?.tokenize("/")
      if (urlParts?.size >= 5) {
        request.put('buildHost', "${urlParts[0]}//${urlParts[1]}/".toString())
      }
    }
  }

  //Common trait for DetailExtractor
  private trait DetailExtractor {

    abstract boolean tryToExtractBuildDetails(Map buildInfo, Map request)

    void extractCommitHash(Map buildInfo, Map request) {
      // buildInfo.scm contains a list of maps. Each map contains these keys: name, sha1, branch.
      // If the list contains more than one entry, prefer the first one that is not master and is not develop.
      def commitHash

      if (buildInfo.scm?.size() >= 2) {
        commitHash = buildInfo.scm.find {
          it.branch != "master" && it.branch != "develop"
        }?.sha1
      }
      if (!commitHash && buildInfo.scm) {
        commitHash = buildInfo.scm.first().sha1
      }
      if (commitHash) {
        request.put('commitHash', commitHash)
      }
    }
  }
}
