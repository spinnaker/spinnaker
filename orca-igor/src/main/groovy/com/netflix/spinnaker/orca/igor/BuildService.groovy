/*
 * Copyright 2015 Netflix, Inc.
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


package com.netflix.spinnaker.orca.igor

import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.util.UriUtils

@CompileStatic
@Component
class BuildService {

  @Autowired
  IgorService igorService

  private String encode(uri) {
    return UriUtils.encodeFragment(uri.toString(), "UTF-8")
  }

  String build(String master, String jobName, Map<String, String> queryParams) {
    return igorService.build(master, encode(jobName), queryParams)
  }

  String stop(String master, String jobName, String queuedBuild, Integer buildNumber) {
    return igorService.stop(master, jobName, queuedBuild, buildNumber)
  }

  Map queuedBuild(String master, String item) {
    return igorService.queuedBuild(master, item)
  }

  Map<String, Object> getBuild(Integer buildNumber, String master, String job) {
    return igorService.getBuild(buildNumber, master, encode(job))
  }

  Map<String, Object> getPropertyFile(Integer buildNumber, String fileName, String master, String job) {
    return igorService.getPropertyFile(buildNumber, fileName, master, encode(job))
  }

  List compareCommits(String repoType, String projectKey, String repositorySlug, Map<String, String> requestParams) {
    return igorService.compareCommits(repoType, projectKey, repositorySlug, requestParams)
  }
}
