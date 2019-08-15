/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.docker.registry.provider.agent


import com.netflix.spinnaker.clouddriver.docker.registry.DockerRegistryCloudProvider
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerRegistryClient
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerRegistryTags
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryCredentials
import spock.lang.Specification

import java.time.Instant

class DockerRegistryImageCachingAgentTest extends Specification {

  DockerRegistryImageCachingAgent agent
  def credentials = Mock(DockerRegistryCredentials)
  def provider = Mock(DockerRegistryCloudProvider)
  def client = Mock(DockerRegistryClient)

  def setup() {
    credentials.client >> client
    agent = new DockerRegistryImageCachingAgent(provider, "test-docker", credentials, 0, 1, 1, "test-registry")
  }

  def "tags loaded from docker registry should be cached"() {
    given:
    credentials.repositories >> repositories
    def total = 0
    for (def i = 0; i < repositories.size(); i++) {
      client.getTags(repositories[i]) >> new DockerRegistryTags().tap {
        name=repositories[i]
        tags=repoTags[i]
      }
      total += repoTags[i].size()
    }

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult.cacheResults.get("taggedImage").size() == total
    cacheResult.cacheResults.get("imageId").size() == total
    for (def i = 0; i < total; i++) {
      def repoAndTag = cacheResult.cacheResults.get("taggedImage")[i].attributes.get("name").split(":")
      repoTags[repositories.indexOf(repoAndTag[0])].contains(repoAndTag[1])
      cacheResult.cacheResults.get("taggedImage")[i].attributes.get("digest") == null
      cacheResult.cacheResults.get("taggedImage")[i].attributes.get("date") == null
    }

    where:
    repositories          | repoTags
    ["repo-1"]            | [["tag-1", "tag-2"]]
    ["repo-1", "repo-2" ] | [["tag-1-1"], ["tag-2-1", "tag-2-2"]]
  }

  def "cached tags should include creation date"() {
    given:
    credentials.sortTagsByDate >> true
    credentials.repositories >> ["repo-1"]
    client.getTags("repo-1") >> new DockerRegistryTags().tap {
      name="repo-1"
      tags=["tag-1", "tag-2"]
    }
    client.getCreationDate(*_) >> Instant.EPOCH

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult.cacheResults.get("taggedImage").size() == 2
    cacheResult.cacheResults.get("imageId").size() == 2
    cacheResult.cacheResults.get("taggedImage")[0].attributes.get("date") == Instant.EPOCH
    cacheResult.cacheResults.get("taggedImage")[1].attributes.get("date") == Instant.EPOCH
  }

  def "cached tags should include digest"() {
    given:
    credentials.trackDigests >> true
    credentials.repositories >> ["repo-1"]
    client.getTags("repo-1") >> new DockerRegistryTags().tap {
      name="repo-1"
      tags=["tag-1", "tag-2"]
    }
    client.getDigest(*_) >> "123"

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult.cacheResults.get("taggedImage").size() == 2
    cacheResult.cacheResults.get("imageId").size() == 2
    cacheResult.cacheResults.get("taggedImage")[0].attributes.get("digest") == "123"
    cacheResult.cacheResults.get("taggedImage")[1].attributes.get("digest") == "123"
  }

}
