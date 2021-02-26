/*
 * Copyright 2017 Schibsted ASA.
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

package com.netflix.spinnaker.clouddriver.docker.registry.controllers

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.clouddriver.docker.registry.DockerRegistryCloudProvider
import com.netflix.spinnaker.clouddriver.docker.registry.cache.Keys
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification

class DockerRegistryImageLookupControllerSpec extends Specification {
  DockerRegistryImageLookupController dockerRegistryImageLookupController

  Set<CacheData> resultData = [
    new CacheData(){
      @Override
      String getId() { Keys.getTaggedImageKey("test-account", "test-repo", "1.0") }

      @Override
      int getTtlSeconds() { return 0 }

      @Override
      Map<String, Collection<String>> getRelationships() { return null }

      @Override
      Map<String, Object> getAttributes() {
        return [
          "account": "test-account",
          "digest": "test-digest",
          "labels": [
            "commitId":    "test-commit",
            "buildNumber": "1",
            "branch":      "test-branch",
            "jobName":     "test-job"
          ]
        ]
      }
    }
  ]

  def accountCredentials = Stub(DockerRegistryNamedAccountCredentials) {
    getCloudProvider() >> DockerRegistryCloudProvider.DOCKER_REGISTRY
    getTrackDigests() >> true
    getRegistry() >> "test-registry"
  }

  def setup() {
    dockerRegistryImageLookupController = new DockerRegistryImageLookupController(
      accountCredentialsProvider: Stub(AccountCredentialsProvider){
        getAll() >> [accountCredentials]
        getCredentials(*_) >> accountCredentials
      },
      cacheView: Stub(Cache) {
        filterIdentifiers(_,_) >> ["someID"]
        getAll(*_) >> resultData
      }
    )
  }

  def "GenerateArtifact"() {
    when:
    def result = dockerRegistryImageLookupController.generateArtifact("foo.registry", "my/app", "mytag")

    then:
    result.name              == "my/app"
    result.version           == "mytag"
    result.reference         == "foo.registry/my/app:mytag"
    result.type              == "docker"
    result.metadata.registry == "foo.registry"
  }

  void "When finding images with includeDetails == false"() {
    when:
    def result = dockerRegistryImageLookupController.find(new DockerRegistryImageLookupController.LookupOptions(includeDetails: false))

    then:
    result.size() == 1
    result[0].account       == "test-account"
    result[0].digest        == "test-digest"
    result[0].commitId      == null
    result[0].buildNumber   == null
    result[0].artifact.type == "docker"
    result[0].artifact.metadata.registry == "test-registry"
  }

  void "When finding images with includeDetails == true"() {
    when:
    def result = dockerRegistryImageLookupController.find(new DockerRegistryImageLookupController.LookupOptions(includeDetails: true))

    then:
    result.size() == 1
    result[0].account       == "test-account"
    result[0].digest        == "test-digest"
    result[0].commitId      == "test-commit"
    result[0].branch        == "test-branch"
    result[0].buildNumber   == "1"
    result[0].artifact.type == "docker"
    result[0].artifact.metadata.registry == "test-registry"
    result[0].artifact.metadata.labels != null
    result[0].artifact.metadata.labels.jobName == "test-job"
  }

  void "When finding images with no metadata and includeDetails == true"() {
    setup:
    def noLabelResultData = [
      new CacheData(){
        @Override
        String getId() { Keys.getTaggedImageKey("test-account", "test-repo", "1.0") }

        @Override
        int getTtlSeconds() { return 0 }

        @Override
        Map<String, Collection<String>> getRelationships() { return null }

        @Override
        Map<String, Object> getAttributes() { return ["account": "test-account", "digest": "test-digest"] }
      }
    ]

    dockerRegistryImageLookupController = new DockerRegistryImageLookupController(
      accountCredentialsProvider: Stub(AccountCredentialsProvider){
        getAll() >> [accountCredentials]
        getCredentials(*_) >> accountCredentials
      },
      cacheView: Stub(Cache) {
        filterIdentifiers(_,_) >> ["someID"]
        getAll(*_) >> noLabelResultData
      }
    )

    when:
    def result = dockerRegistryImageLookupController.find(new DockerRegistryImageLookupController.LookupOptions(includeDetails: true))

    then:
    result.size() == 1
    result[0].account       == "test-account"
    result[0].digest        == "test-digest"
    result[0].commitId      == null
    result[0].buildNumber   == null
    result[0].artifact.type == "docker"
    result[0].artifact.metadata.registry == "test-registry"
    result[0].artifact.metadata.labels == null
  }
}
