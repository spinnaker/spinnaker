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

import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.clouddriver.docker.registry.DockerRegistryCloudProvider
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerRegistryClient
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerRegistryTags
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryCredentials
import retrofit.RetrofitError
import spock.lang.Specification

import java.time.Instant

class DockerRegistryImageCachingAgentTest extends Specification {

  def KEY_PREFIX = "dockerRegistry"
  def ACCOUNT_NAME = "test-docker"
  def REGISTRY_NAME = "test-registry"
  def CACHE_GROUP_TAGGED_IMAGE = "taggedImage"
  def CACHE_GROUP_IMAGE_ID = "imageId"

  DockerRegistryImageCachingAgent agent
  def credentials = Mock(DockerRegistryCredentials)
  def provider = Mock(DockerRegistryCloudProvider)
  def client = Mock(DockerRegistryClient)

  def setup() {
    credentials.client >> client
    agent = new DockerRegistryImageCachingAgent(provider, ACCOUNT_NAME, credentials, 0, 1, 1, REGISTRY_NAME)
  }

  def "tags loaded from docker registry should be cached"() {
    given:
    credentials.repositories >> ["repo-1", "repo-2"]
    client.getTags("repo-1") >> new DockerRegistryTags().tap {
      name = "repo-1"
      tags = ["tag-1-1"]
    }
    client.getTags("repo-2") >> new DockerRegistryTags().tap {
      name = "repo-2"
      tags = ["tag-2-1", "tag-2-2"]
    }
    def repoTagSequence = [
      ["repo-1", "tag-1-1"],
      ["repo-2", "tag-2-1"],
      ["repo-2", "tag-2-2"],
    ]

    when:
    def cacheResult = agent.loadData(null)

    then:
    sortCacheResult(cacheResult)
    def cacheResultImageIds = cacheResult.cacheResults.get(CACHE_GROUP_IMAGE_ID)
    for (int i = 0; i < cacheResultImageIds.size(); i++) {
      assert cacheResultImageIds[i].id == buildImageIdCacheKey(repoTagSequence[i][0], repoTagSequence[i][1])
      assert cacheResultImageIds[i].attributes.get("tagKey") == buildTaggedImageCacheKey(repoTagSequence[i][0], repoTagSequence[i][1])
      assert cacheResultImageIds[i].attributes.get("account") == ACCOUNT_NAME
    }
    def cacheResultTaggedImages = cacheResult.cacheResults.get(CACHE_GROUP_TAGGED_IMAGE)
    for (int i = 0; i < cacheResultTaggedImages.size(); i++) {
      assert cacheResultTaggedImages[i].id == buildTaggedImageCacheKey(repoTagSequence[i][0], repoTagSequence[i][1])
      assert cacheResultTaggedImages[i].attributes.get("name") == "${repoTagSequence[i][0]}:${repoTagSequence[i][1]}"
      assert cacheResultTaggedImages[i].attributes.get("account") == ACCOUNT_NAME
      assert cacheResultTaggedImages[i].attributes.get("digest") == null
      assert cacheResultTaggedImages[i].attributes.get("date") == null
    }
  }

  def "cached tags should include creation date"() {
    given:
    credentials.sortTagsByDate >> true
    credentials.repositories >> ["repo-1"]
    client.getTags("repo-1") >> new DockerRegistryTags().tap {
      name="repo-1"
      tags=["tag-1", "tag-2"]
    }
    def repoTagSequence = [
      ["repo-1", "tag-1"],
      ["repo-1", "tag-2"],
    ]
    client.getCreationDate("repo-1", "tag-1") >> Instant.ofEpochSecond(0)
    client.getCreationDate("repo-1", "tag-2") >> Instant.ofEpochSecond(1)

    when:
    def cacheResult = agent.loadData(null)

    then:
    sortCacheResult(cacheResult)
    def cacheResultImageIds = cacheResult.cacheResults.get(CACHE_GROUP_IMAGE_ID)
    for (int i = 0; i < cacheResultImageIds.size(); i++) {
      assert cacheResultImageIds[i].id == buildImageIdCacheKey(repoTagSequence[i][0], repoTagSequence[i][1])
      assert cacheResultImageIds[i].attributes.get("tagKey") == buildTaggedImageCacheKey(repoTagSequence[i][0], repoTagSequence[i][1])
      assert cacheResultImageIds[i].attributes.get("account") == ACCOUNT_NAME
    }
    def cacheResultTaggedImages = cacheResult.cacheResults.get(CACHE_GROUP_TAGGED_IMAGE)
    for (int i = 0; i < cacheResultTaggedImages.size(); i++) {
      assert cacheResultTaggedImages[i].id == buildTaggedImageCacheKey(repoTagSequence[i][0], repoTagSequence[i][1])
      assert cacheResultTaggedImages[i].attributes.get("name") == "${repoTagSequence[i][0]}:${repoTagSequence[i][1]}"
      assert cacheResultTaggedImages[i].attributes.get("account") == ACCOUNT_NAME
      assert cacheResultTaggedImages[i].attributes.get("digest") == null
      assert cacheResultTaggedImages[i].attributes.get("date") == Instant.ofEpochSecond(i)
    }
  }

  def "cached tags should include digest"() {
    given:
    credentials.trackDigests >> true
    credentials.repositories >> ["repo-1"]
    client.getTags("repo-1") >> new DockerRegistryTags().tap {
      name="repo-1"
      tags=["tag-1", "tag-2"]
    }
    def repoTagSequence = [
      ["repo-1", "tag-1"],
      ["repo-1", "tag-2"],
    ]
    client.getDigest("repo-1", "tag-1") >> "repo-1_tag-1"
    client.getDigest("repo-1", "tag-2") >> "repo-1_tag-2"

    when:
    def cacheResult = agent.loadData(null)

    then:
    sortCacheResult(cacheResult)
    def cacheResultImageIds = cacheResult.cacheResults.get(CACHE_GROUP_IMAGE_ID)
    for (int i = 0; i < cacheResultImageIds.size(); i++) {
      assert cacheResultImageIds[i].id == buildImageIdCacheKey(repoTagSequence[i][0], repoTagSequence[i][1])
      assert cacheResultImageIds[i].attributes.get("tagKey") == buildTaggedImageCacheKey(repoTagSequence[i][0], repoTagSequence[i][1])
      assert cacheResultImageIds[i].attributes.get("account") == ACCOUNT_NAME
    }
    def cacheResultTaggedImages = cacheResult.cacheResults.get(CACHE_GROUP_TAGGED_IMAGE)
    for (int i = 0; i < cacheResultTaggedImages.size(); i++) {
      assert cacheResultTaggedImages[i].id == buildTaggedImageCacheKey(repoTagSequence[i][0], repoTagSequence[i][1])
      assert cacheResultTaggedImages[i].attributes.get("name") == "${repoTagSequence[i][0]}:${repoTagSequence[i][1]}"
      assert cacheResultTaggedImages[i].attributes.get("account") == ACCOUNT_NAME
      assert cacheResultTaggedImages[i].attributes.get("digest") == "${repoTagSequence[i][0]}_${repoTagSequence[i][1]}"
      assert cacheResultTaggedImages[i].attributes.get("date") == null
    }
  }

  def "cached tags should include label if inspectDigest is true"() {
    given:
    credentials.inspectDigests >> true
    credentials.repositories >> ["repo-1"]
    client.getTags("repo-1") >> new DockerRegistryTags().tap { name="repo-1"; tags=["tag-1"] }
    client.getConfigDigest("repo-1", "tag-1") >> "digest-1"
    client.getDigestContent("repo-1", "digest-1") >> ["config": ["Labels": ["commitId": "id1", "buildNumber": "1"] ]]

    when:
    def cacheResult = agent.loadData(null)

    then:
    sortCacheResult(cacheResult)
    def cacheResultTaggedImages = cacheResult.cacheResults.get(CACHE_GROUP_TAGGED_IMAGE)
    for (int i = 0; i < cacheResultTaggedImages.size(); i++) {
      assert cacheResultTaggedImages[i].attributes.get("digest") == "digest-1"
      assert cacheResultTaggedImages[i].attributes.get("labels") == ["commitId": "id1", "buildNumber": "1"]
    }
  }

  def "error loading tags returns empty result"() {
    given:
    credentials.repositories >> ["repo-1"]
    client.getTags("repo-1") >> {
      throw new IOException()
    }

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult.cacheResults.get(CACHE_GROUP_IMAGE_ID).size() == 0
    cacheResult.cacheResults.get(CACHE_GROUP_TAGGED_IMAGE).size() == 0
  }

  def "error loading tag date should set to null date attribute"() {
    given:
    credentials.sortTagsByDate >> true
    credentials.repositories >> ["repo-1"]
    client.getTags("repo-1") >> new DockerRegistryTags().tap {
      name="repo-1"
      tags=["tag-1", "tag-2"]
    }
    def repoTagSequence = [
      ["repo-1", "tag-1"],
      ["repo-1", "tag-2"],
    ]
    client.getCreationDate("repo-1", "tag-1") >> {
      throw RetrofitError.httpError("", null, null, null)
    }
    client.getCreationDate("repo-1", "tag-2") >> Instant.EPOCH

    when:
    def cacheResult = agent.loadData(null)

    then:
    sortCacheResult(cacheResult)
    def cacheResultImageIds = cacheResult.cacheResults.get(CACHE_GROUP_IMAGE_ID)
    for (int i = 0; i < cacheResultImageIds.size(); i++) {
      assert cacheResultImageIds[i].id == buildImageIdCacheKey(repoTagSequence[i][0], repoTagSequence[i][1])
      assert cacheResultImageIds[i].attributes.get("tagKey") == buildTaggedImageCacheKey(repoTagSequence[i][0], repoTagSequence[i][1])
      assert cacheResultImageIds[i].attributes.get("account") == ACCOUNT_NAME
    }
    def cacheResultTaggedImages = cacheResult.cacheResults.get(CACHE_GROUP_TAGGED_IMAGE)
    for (int i = 0; i < cacheResultTaggedImages.size(); i++) {
      assert cacheResultTaggedImages[i].id == buildTaggedImageCacheKey(repoTagSequence[i][0], repoTagSequence[i][1])
      assert cacheResultTaggedImages[i].attributes.get("name") == "${repoTagSequence[i][0]}:${repoTagSequence[i][1]}"
      assert cacheResultTaggedImages[i].attributes.get("account") == ACCOUNT_NAME
      assert cacheResultTaggedImages[i].attributes.get("digest") == null
      assert cacheResultTaggedImages[i].attributes.get("date") == (i == 0 ? null : Instant.EPOCH)
    }
  }

  def "error loading tag digest should not cache that tag"() {
    given:
    credentials.trackDigests >> true
    credentials.repositories >> ["repo-1"]
    client.getTags("repo-1") >> new DockerRegistryTags().tap {
      name="repo-1"
      tags=["tag-1", "tag-2"]
    }
    client.getDigest("repo-1", "tag-1") >> {
      throw new IOException()
    }
    client.getDigest("repo-1", "tag-2") >> "repo-1_tag-2"

    when:
    def cacheResult = agent.loadData(null)

    then:
    sortCacheResult(cacheResult)
    def cacheResultImageIds = cacheResult.cacheResults.get(CACHE_GROUP_IMAGE_ID)
    cacheResultImageIds.size() == 1
    cacheResultImageIds[0].id == buildImageIdCacheKey("repo-1", "tag-2")
    cacheResultImageIds[0].attributes.get("tagKey") == buildTaggedImageCacheKey("repo-1", "tag-2")
    cacheResultImageIds[0].attributes.get("account") == ACCOUNT_NAME
    def cacheResultTaggedImages = cacheResult.cacheResults.get(CACHE_GROUP_TAGGED_IMAGE)
    cacheResultTaggedImages.size() == 1
    cacheResultTaggedImages[0].id == buildTaggedImageCacheKey("repo-1", "tag-2")
    cacheResultTaggedImages[0].attributes.get("name") == "repo-1:tag-2"
    cacheResultTaggedImages[0].attributes.get("account") == ACCOUNT_NAME
    cacheResultTaggedImages[0].attributes.get("digest") == "repo-1_tag-2"
    cacheResultTaggedImages[0].attributes.get("date") == null
  }

  def "empty tags should not be cached"() {
    given:
    credentials.repositories >> ["repo-1"]
    client.getTags("repo-1") >> new DockerRegistryTags().tap {
      name="repo-1"
      tags=["tag-1", ""]
    }

    when:
    def cacheResult = agent.loadData(null)

    then:
    sortCacheResult(cacheResult)
    def cacheResultImageIds = cacheResult.cacheResults.get(CACHE_GROUP_IMAGE_ID)
    cacheResultImageIds.size() == 1
    cacheResultImageIds[0].id == buildImageIdCacheKey("repo-1", "tag-1")
    cacheResultImageIds[0].attributes.get("tagKey") == buildTaggedImageCacheKey("repo-1", "tag-1")
    cacheResultImageIds[0].attributes.get("account") == ACCOUNT_NAME
    def cacheResultTaggedImages = cacheResult.cacheResults.get(CACHE_GROUP_TAGGED_IMAGE)
    cacheResultTaggedImages.size() == 1
    cacheResultTaggedImages[0].id == buildTaggedImageCacheKey("repo-1", "tag-1")
    cacheResultTaggedImages[0].attributes.get("name") == "repo-1:tag-1"
    cacheResultTaggedImages[0].attributes.get("account") == ACCOUNT_NAME
    cacheResultTaggedImages[0].attributes.get("digest") == null
    cacheResultTaggedImages[0].attributes.get("date") == null
  }


  private String buildTaggedImageCacheKey(repo, tag) {
    "${KEY_PREFIX}:${CACHE_GROUP_TAGGED_IMAGE}:${ACCOUNT_NAME}:${repo}:${tag}"
  }

  private String buildImageIdCacheKey(repo, tag) {
    "${KEY_PREFIX}:${CACHE_GROUP_IMAGE_ID}:${REGISTRY_NAME}/${repo}:${tag}"
  }

  private void sortCacheResult(CacheResult cacheResult) {
    cacheResult.cacheResults.get(CACHE_GROUP_TAGGED_IMAGE).sort {
      it.id
    }
    cacheResult.cacheResults.get(CACHE_GROUP_IMAGE_ID).sort {
      it.id
    }
  }
}
