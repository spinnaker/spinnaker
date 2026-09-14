/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.DescribeLaunchTemplateVersionsResponse
import software.amazon.awssdk.services.ec2.model.DescribeLaunchTemplatesResponse
import software.amazon.awssdk.services.ec2.model.LaunchTemplate
import software.amazon.awssdk.services.ec2.model.LaunchTemplateVersion
import software.amazon.awssdk.services.ec2.model.ResponseLaunchTemplateData
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.jackson.AwsSdkV2Module
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LAUNCH_TEMPLATES

class AmazonLaunchTemplateCachingAgentSpec extends Specification {
  def registry = new NoopRegistry()
  def objectMapper = AmazonObjectMapperConfigurer.createConfigured().registerModule(new AwsSdkV2Module())
  def region = "us-east-1"
  def account = "test"
  def amazonClientProvider = Mock(AmazonClientProvider)

  def credentials = Stub(NetflixAmazonCredentials) {
    getName() >> account
  }

  @Shared
  def providerCache = Mock(ProviderCache)

  @Subject
  def agent = new AmazonLaunchTemplateCachingAgent(amazonClientProvider, credentials, region, objectMapper, registry)

  def "should load launch templates"() {
    given:
    def ec2 = Mock(Ec2Client)
    def lt1 = LaunchTemplate.builder().launchTemplateName("lt-1").launchTemplateId("lt-1").latestVersionNumber(1L).defaultVersionNumber(0L).build()
    def lt2 = LaunchTemplate.builder().launchTemplateName("lt-2").launchTemplateId("lt-2").latestVersionNumber(0L).defaultVersionNumber(0L).build()

    def lt1Version0 = LaunchTemplateVersion.builder()
      .launchTemplateId(lt1.launchTemplateId())
      .defaultVersion(true)
      .versionNumber(0L)
      .launchTemplateData(ResponseLaunchTemplateData.builder().imageId("ami-10").build())
      .build()

    def lt1Version1 = LaunchTemplateVersion.builder()
      .launchTemplateId(lt1.launchTemplateId())
      .defaultVersion(false)
      .versionNumber(1L)
      .launchTemplateData(ResponseLaunchTemplateData.builder().imageId("ami-11").build())
      .build()

    def lt2Version0 = LaunchTemplateVersion.builder()
      .launchTemplateId(lt2.launchTemplateId())
      .defaultVersion(true)
      .versionNumber(0L)
      .launchTemplateData(ResponseLaunchTemplateData.builder().imageId("ami-20").build())
      .build()

    and:
    amazonClientProvider.getAmazonEC2V2(credentials, region) >> ec2

    when:
    def result = agent.loadData(providerCache).cacheResults[LAUNCH_TEMPLATES.ns]

    then:
    1 * ec2.describeLaunchTemplates(_) >> DescribeLaunchTemplatesResponse.builder().launchTemplates([lt1, lt2]).build()
    1 * ec2.describeLaunchTemplateVersions(_) >> DescribeLaunchTemplateVersionsResponse.builder().launchTemplateVersions([lt1Version0, lt1Version1, lt2Version0]).build()

    result.size() == 2
    def lt1Result = result.find { it.attributes.launchTemplateName == lt1.launchTemplateName() }
    def lt1v1Image = Keys.getImageKey(lt1Version1.launchTemplateData().imageId(), account, region)
    def lt1v0Image = Keys.getImageKey(lt1Version0.launchTemplateData().imageId(), account, region)

    def lt2Result = result.find { it.attributes.launchTemplateName == lt2.launchTemplateName() }
    def lt2Image = Keys.getImageKey(lt2Version0.launchTemplateData().imageId(), account, region)
    lt1Result.attributes.versions.size() == 2
    lt1Result.attributes.latestVersion == lt1Version1
    lt1Result.relationships.images == [lt1v0Image, lt1v1Image] as Set

    lt2Result.attributes.versions.size() == 1
    lt2Result.attributes.latestVersion == lt2Version0
    lt2Result.relationships.images == [lt2Image] as Set
  }
}
