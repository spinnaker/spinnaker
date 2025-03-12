/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco.executor

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.api.BakeStatus
import com.netflix.spinnaker.rosco.jobs.BakeRecipe
import com.netflix.spinnaker.rosco.persistence.BakeStore
import com.netflix.spinnaker.rosco.persistence.RedisBackedBakeStore
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.jobs.JobExecutor
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class BakePollerSpec extends Specification implements TestDefaults {

  private static final String JOB_ID = "123"
  private static final String AMI_ID = "ami-3cf4a854"
  private static final String IMAGE_NAME = "some-image"
  private static final String LOGS_CONTENT = "Some logs content..."

  @Unroll
  void 'scheduled update queries job executor and stores status and logs when incomplete'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def jobExecutorMock = Mock(JobExecutor)
      def incompleteBakeStatus = new BakeStatus(id: JOB_ID,
                                                resource_id: JOB_ID,
                                                state: bakeState,
                                                result: bakeResult,
                                                logsContent: LOGS_CONTENT)

      @Subject
      def bakePoller = new BakePoller(bakeStore: bakeStoreMock,
                                      executor: jobExecutorMock,
                                      cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                      registry: new DefaultRegistry())

    when:
      bakePoller.updateBakeStatusAndLogs(JOB_ID)

    then:
      1 * jobExecutorMock.updateJob(JOB_ID) >> incompleteBakeStatus
      1 * bakeStoreMock.updateBakeStatus(incompleteBakeStatus)
      numStatusLookups * bakeStoreMock.retrieveRegionById(JOB_ID) >> SOME_REGION
      numStatusLookups * bakeStoreMock.retrieveBakeStatusById(JOB_ID) >> incompleteBakeStatus

    where:
      bakeState                 | bakeResult                | numStatusLookups
      BakeStatus.State.RUNNING  | null                      | 0
      BakeStatus.State.CANCELED | BakeStatus.Result.FAILURE | 1
  }

  void 'scheduled update queries job executor and stores status, details and logs when complete'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def jobExecutorMock = Mock(JobExecutor)
      def completeBakeStatus = new BakeStatus(id: JOB_ID,
                                              resource_id: JOB_ID,
                                              state: bakeState,
                                              result: bakeResult,
                                              logsContent: "$LOGS_CONTENT\n$LOGS_CONTENT")
      def bakeRequest = new BakeRequest(build_info_url: SOME_BUILD_INFO_URL)
      def bakeRecipe = new BakeRecipe(name: SOME_BAKE_RECIPE_NAME, version: SOME_APP_VERSION_STR, command: [])
      def bakeDetails = new Bake(id: JOB_ID, ami: AMI_ID, image_name: IMAGE_NAME)

      @Subject
      def bakePoller = new BakePoller(bakeStore: bakeStoreMock,
                                      executor: jobExecutorMock,
                                      cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                      registry: new DefaultRegistry())

    when:
      bakePoller.updateBakeStatusAndLogs(JOB_ID)

    then:
      1 * jobExecutorMock.updateJob(JOB_ID) >> completeBakeStatus
      1 * bakeStoreMock.retrieveCloudProviderById(JOB_ID) >> BakeRequest.CloudProviderType.gce.toString()
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      2 * bakeStoreMock.retrieveRegionById(JOB_ID) >> SOME_REGION  // 1 for metrics
      1 * cloudProviderBakeHandlerMock.scrapeCompletedBakeResults(SOME_REGION, JOB_ID, "$LOGS_CONTENT\n$LOGS_CONTENT") >> bakeDetails
      1 * bakeStoreMock.retrieveBakeRequestById(JOB_ID) >> bakeRequest
      1 * bakeStoreMock.retrieveBakeRecipeById(JOB_ID) >> bakeRecipe
      1 * bakeStoreMock.updateBakeDetails(bakeDetails)
      1 * bakeStoreMock.updateBakeStatus(completeBakeStatus)
      1 * bakeStoreMock.retrieveBakeStatusById(JOB_ID) >> completeBakeStatus

    where:
      bakeState                  | bakeResult
      BakeStatus.State.COMPLETED | BakeStatus.Result.SUCCESS
  }

  void 'scheduled update stores error and status when running job status cannot be retrieved'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def jobExecutorMock = Mock(JobExecutor)

      @Subject
      def bakePoller = new BakePoller(bakeStore: bakeStoreMock,
                                      executor: jobExecutorMock,
                                      cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                      registry: new DefaultRegistry())

    when:
      bakePoller.updateBakeStatusAndLogs(JOB_ID)

    then:
      1 * jobExecutorMock.updateJob(JOB_ID) >> null
      1 * bakeStoreMock.storeBakeError(JOB_ID, "Unable to retrieve status for '$JOB_ID'.")
      1 * bakeStoreMock.cancelBakeById(JOB_ID)
      1 * bakeStoreMock.retrieveRegionById(JOB_ID) >> SOME_REGION
      1 * bakeStoreMock.retrieveBakeStatusById(JOB_ID) >> new BakeStatus()
  }

  void 'decorate the bakeDetails with an artifact if bake is successful'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def jobExecutorMock = Mock(JobExecutor)
      def bakeRequest = new BakeRequest(build_info_url: SOME_BUILD_INFO_URL)
      def bakeRecipe = new BakeRecipe(name: SOME_BAKE_RECIPE_NAME, version: SOME_APP_VERSION_STR, command: [])
      def bakedArtifact = Artifact.builder()
        .name(bakeRecipe.name)
        .version(bakeRecipe.version)
        .type("${DOCKER_CLOUD_PROVIDER}/image")
        .reference(AMI_ID)
        .metadata([
          build_info_url: bakeRequest.build_info_url,
          build_number: bakeRequest.build_number])
        .build()
      def bakeDetails = new Bake(id: JOB_ID, ami: AMI_ID, image_name: IMAGE_NAME, artifact: bakedArtifact)
      def decoratedBakeDetails = new Bake(id: JOB_ID, ami: AMI_ID, image_name: IMAGE_NAME, artifact: bakedArtifact)

      @Subject
      def bakePoller = new BakePoller(
        bakeStore: bakeStoreMock,
        executor: jobExecutorMock,
        cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
        registry: new DefaultRegistry())

    when:
      bakePoller.completeBake(JOB_ID, LOGS_CONTENT)

    then:
      1 * bakeStoreMock.retrieveCloudProviderById(JOB_ID) >> DOCKER_CLOUD_PROVIDER.toString()
      1 * cloudProviderBakeHandlerRegistryMock.lookup(DOCKER_CLOUD_PROVIDER) >> cloudProviderBakeHandlerMock
      1 * bakeStoreMock.retrieveRegionById(JOB_ID) >> SOME_REGION
      1 * cloudProviderBakeHandlerMock.scrapeCompletedBakeResults(SOME_REGION, JOB_ID, LOGS_CONTENT) >> bakeDetails
      1 * cloudProviderBakeHandlerMock.produceArtifactDecorationFrom(bakeRequest, bakeRecipe, bakeDetails, DOCKER_CLOUD_PROVIDER.toString(), SOME_REGION) >> bakedArtifact
      1 * bakeStoreMock.retrieveBakeRequestById(JOB_ID) >> bakeRequest
      1 * bakeStoreMock.retrieveBakeRecipeById(JOB_ID) >> bakeRecipe
      1 * bakeStoreMock.updateBakeDetails(decoratedBakeDetails)
  }

  void 'sets the name and uuid on baked artifacts'() {
    setup:
    def bakedArtifact = Artifact.builder()
            .type("aws/image")
            .location("eu-north-1")
            .reference("ami-076cf277a86b6e5b4")
            .build()

    def bakeStoreMock = Mock(BakeStore) {
      retrieveCloudProviderById(JOB_ID) >> BakeRequest.CloudProviderType.aws.toString()
      retrieveRegionById(JOB_ID) >> SOME_REGION
      retrieveBakeRequestById(JOB_ID) >> new BakeRequest(build_info_url: SOME_BUILD_INFO_URL)
      retrieveBakeRecipeById(JOB_ID) >> new BakeRecipe(name: SOME_BAKE_RECIPE_NAME, version: SOME_APP_VERSION_STR, command: [])
    }

    def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler) {
      scrapeCompletedBakeResults(SOME_REGION, JOB_ID, _ as String) >> new Bake(id: JOB_ID, ami: AMI_ID, artifacts: [bakedArtifact])
      produceArtifactDecorationFrom(_ as BakeRequest, _ as BakeRecipe, _ as Bake, _ as String, _ as String) >> bakedArtifact
      deleteArtifactFile(_ as String) >> {}
    }

    def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry) {
      lookup(BakeRequest.CloudProviderType.aws) >> cloudProviderBakeHandlerMock
    }

    @Subject
    def bakePoller = new BakePoller(
            bakeStore: bakeStoreMock,
            executor: Mock(JobExecutor),
            cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
            registry: new DefaultRegistry())

    when:
    bakePoller.completeBake(JOB_ID, LOGS_CONTENT)

    then:
    1 * bakeStoreMock.updateBakeDetails(_ as Bake) >> { Bake bake ->
      assert bake.getArtifacts().size() == 1
      Artifact artifact = bake.getArtifacts().get(0)
      assert artifact.getName() == SOME_BAKE_RECIPE_NAME
      assert artifact.getUuid() == JOB_ID
      assert artifact.getType() == "aws/image"
      assert artifact.getReference() == "ami-076cf277a86b6e5b4"
    }
  }

}
