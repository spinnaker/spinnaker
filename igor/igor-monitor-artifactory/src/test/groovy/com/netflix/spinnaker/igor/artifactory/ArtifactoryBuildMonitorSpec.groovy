/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.igor.artifactory

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.artifactory.model.ArtifactoryRepositoryType
import com.netflix.spinnaker.igor.artifactory.model.ArtifactorySearch
import com.netflix.spinnaker.igor.config.ArtifactoryProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.polling.LockService
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.springframework.scheduling.TaskScheduler
import spock.lang.Specification

class ArtifactoryBuildMonitorSpec extends Specification {
  ArtifactoryCache cache = Mock(ArtifactoryCache)
  EchoService echoService = Mock()
  LockService lockService = Mock()
  IgorConfigurationProperties igorConfigurationProperties = new IgorConfigurationProperties()
  ArtifactoryBuildMonitor monitor

  MockWebServer mockArtifactory = new MockWebServer()

  ArtifactoryBuildMonitor monitor(search, lockService = null) {
    monitor = new ArtifactoryBuildMonitor(
      igorConfigurationProperties,
      new NoopRegistry(),
      new DynamicConfigService.NoopDynamicConfig(),
      new DiscoveryStatusListener(true),
      Optional.ofNullable(lockService),
      Optional.of(echoService),
      cache,
      new ArtifactoryProperties(searches: [search]),
      Mock(TaskScheduler)
    )

    return monitor
  }

  def mavenSearch(url) {
    return new ArtifactorySearch(
      baseUrl: url,
      repo: 'libs-releases-local',
      repoType: ArtifactoryRepositoryType.MAVEN
    )
  }

  def helmSearch(url) {
    return new ArtifactorySearch(
      baseUrl: url,
      repo: 'helm-local',
      repoType: ArtifactoryRepositoryType.HELM
    )
  }

  def 'should handle any failure to talk to artifactory graciously' () {
    given:
    mockArtifactory.enqueue(new MockResponse().setResponseCode(400))

    when:
    monitor(mavenSearch(mockArtifactory.url(''))).poll(false)

    then:
    notThrown(Exception)
  }

  def 'does not add extra path separators with non-empty context root'() {
    given:
    mockArtifactory.enqueue(new MockResponse().setResponseCode(200).setBody('{"results": []}'))

    when:
    monitor(mavenSearch(mockArtifactory.url(contextRoot))).poll(false)

    then:
    mockArtifactory.takeRequest().path == "/${contextRoot}api/search/aql"

    where:
    contextRoot << ['artifactory/', '']
  }

  def 'strips out invalid characters when creating a lock name'() {
    given:
    mockArtifactory.enqueue(new MockResponse().setResponseCode(200).setBody('{"results": []}'))

    when:
    monitor(mavenSearch("http://localhost:64610"), lockService).poll(false)

    then:
    1 * lockService.acquire("artifactoryPublishingMonitor.httplocalhost64610libs-releases-local", _, _)
  }

  def 'generates correct AQL for maven repository type'() {
    given:
    mockArtifactory.enqueue(new MockResponse().setResponseCode(200).setBody('{"results": []}'))

    when:
    monitor(mavenSearch(mockArtifactory.url(''))).poll(false)

    then:
    mockArtifactory.takeRequest().getBody().readUtf8() == "items.find({\"repo\":\"libs-releases-local\"," +
      "\"modified\":{\"\$last\":\"36000minutes\"},\"path\":{\"\$match\":\"*\"},\"name\":{\"\$match\":\"*.pom\"}})" +
      ".include(\"path\",\"repo\",\"name\",\"artifact.module.build\")"
  }

  def 'generates correct AQL for helm repository type'() {
    given:
    mockArtifactory.enqueue(new MockResponse().setResponseCode(200).setBody('{"results": []}'))

    when:
    monitor(helmSearch(mockArtifactory.url(''))).poll(false)

    then:
    mockArtifactory.takeRequest().getBody().readUtf8() == "items.find({\"repo\":\"helm-local\"," +
      "\"modified\":{\"\$last\":\"36000minutes\"},\"path\":{\"\$match\":\"*\"},\"name\":{\"\$match\":\"*.tgz\"}})" +
      ".include(\"path\",\"repo\",\"name\",\"artifact.module.build\",\"@chart.name\",\"@chart.version\")"
  }
}
