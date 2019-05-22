/*
 * Copyright 2018 Datadog, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheStatusService
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import static java.net.HttpURLConnection.HTTP_ACCEPTED
import static java.net.HttpURLConnection.HTTP_OK

class ManifestForceCacheRefreshTaskSpec extends Specification {
  static final String ACCOUNT = "k8s"
  static final String PROVIDER = "kubernetes"
  static final String REFRESH_TYPE = "manifest"

  def now = Instant.now()

  def cacheService = Mock(CloudDriverCacheService)
  def cacheStatusService = Mock(CloudDriverCacheStatusService)
  def objectMapper = new ObjectMapper()

  def registry = new DefaultRegistry()
  @Subject task = new ManifestForceCacheRefreshTask(
    registry,
    cacheService,
    cacheStatusService,
    objectMapper,
    Clock.fixed(now, ZoneId.of("UTC"))
  )

  def "auto Succeed from timeout increments counter"() {
    given:
    def stage = mockStage([:])
    stage.setStartTime(now.minusMillis(TimeUnit.MINUTES.toMillis(13)).toEpochMilli())
    def taskResult = task.execute(stage)

    expect:
    taskResult.getStatus().isSuccessful()
    registry.timer("manifestForceCacheRefreshTask.duration", "success", "true", "outcome", "autoSucceed").count() == 1
  }

  def "returns RUNNING when the refresh request is accepted but not processed"() {
    given:
    def namespace = "my-namespace"
    def manifest = "replicaSet my-replicaset-v014"
    def context = [
        account: ACCOUNT,
        cloudProvider: PROVIDER,
        "outputs.manifestNamesByNamespace": [
          (namespace): [
            manifest
          ]
        ],
    ]
    def refreshDetails = [
      account: ACCOUNT,
      location: namespace,
      name: manifest
    ]
    def stage = mockStage(context)
    stage.setStartTime(now.toEpochMilli())

    when:
    def taskResult = task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, refreshDetails) >> mockResponse(HTTP_ACCEPTED)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING
  }

  def "returns SUCCEEDED when the refresh request is immediately processed"() {
    given:
    def namespace = "my-namespace"
    def manifest = "replicaSet my-replicaset-v014"
    def context = [
      account: ACCOUNT,
      cloudProvider: PROVIDER,
      "outputs.manifestNamesByNamespace": [
        (namespace): [
          manifest
        ]
      ],
    ]
    def refreshDetails = [
      account: ACCOUNT,
      location: namespace,
      name: manifest
    ]
    def stage = mockStage(context)
    stage.setStartTime(now.toEpochMilli())

    when:
    def taskResult = task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, refreshDetails) >> mockResponse(HTTP_OK)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.SUCCEEDED
  }

  def "waits for a pending refresh"() {
    given:
    def namespace = "my-namespace"
    def manifest = "replicaSet my-replicaset-v014"
    def context = [
      account: ACCOUNT,
      cloudProvider: PROVIDER,
      "outputs.manifestNamesByNamespace": [
        (namespace): [
          manifest
        ]
      ],
    ]
    def refreshDetails = [
      account: ACCOUNT,
      location: namespace,
      name: manifest
    ]
    def stage = mockStage(context)

    when:
    def taskResult = task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, refreshDetails) >> mockResponse(HTTP_ACCEPTED)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [pendingRefresh(refreshDetails)]
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [processedRefresh(refreshDetails)]
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.SUCCEEDED
  }

  def "only returns succeeded if a processed refresh exactly matches"() {
    given:
    def namespace = "my-namespace"
    def manifest = "replicaSet my-replicaset-v014"
    def noMatch = "no-match"
    def context = [
      account: ACCOUNT,
      cloudProvider: PROVIDER,
      "outputs.manifestNamesByNamespace": [
        (namespace): [
          manifest
        ]
      ],
    ]
    def refreshDetails = [
      account: ACCOUNT,
      location: namespace,
      name: manifest
    ]
    def stage = mockStage(context)

    when:
    def taskResult = task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, refreshDetails) >> mockResponse(HTTP_ACCEPTED)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [
      processedRefresh(refreshDetails + [account: noMatch]),
      processedRefresh(refreshDetails + [location: noMatch]),
      processedRefresh(refreshDetails + [name: noMatch])
    ]
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, refreshDetails) >> mockResponse(HTTP_ACCEPTED)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING
  }

  def "retries when the cache does not know about the refresh request"() {
    given:
    def namespace = "my-namespace"
    def manifest = "replicaSet my-replicaset-v014"
    def context = [
      account: ACCOUNT,
      cloudProvider: PROVIDER,
      "outputs.manifestNamesByNamespace": [
        (namespace): [
          manifest
        ]
      ],
    ]
    def refreshDetails = [
      account: ACCOUNT,
      location: namespace,
      name: manifest
    ]
    def stage = mockStage(context)

    when:
    def taskResult = task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, refreshDetails) >> mockResponse(HTTP_ACCEPTED)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> []
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, refreshDetails) >> mockResponse(HTTP_ACCEPTED)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [processedRefresh(refreshDetails)]
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.SUCCEEDED
  }

  def "waits until all manifests are processed when one is immediately processed"() {
    given:
    def namespace = "my-namespace"
    def replicaSet = "replicaSet my-replicaset-v014"
    def deployment = "deployment my-deployment"
    def context = [
      account: ACCOUNT,
      cloudProvider: PROVIDER,
      "outputs.manifestNamesByNamespace": [
        (namespace): [
          replicaSet,
          deployment
        ]
      ],
    ]
    def replicaSetRefreshDetails = [
      account: ACCOUNT,
      location: namespace,
      name: replicaSet
    ]
    def deploymentRefreshDetails = [
      account: ACCOUNT,
      location: namespace,
      name: deployment
    ]
    def stage = mockStage(context)

    when:
    def taskResult = task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, replicaSetRefreshDetails) >> mockResponse(HTTP_OK)
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, deploymentRefreshDetails) >> mockResponse(HTTP_ACCEPTED)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [pendingRefresh(deploymentRefreshDetails)]
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [processedRefresh(deploymentRefreshDetails)]
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.SUCCEEDED
  }

  def "waits until all manifests are processed when all are accepted for later processing"() {
    given:
    def namespace = "my-namespace"
    def replicaSet = "replicaSet my-replicaset-v014"
    def deployment = "deployment my-deployment"
    def context = [
      account: ACCOUNT,
      cloudProvider: PROVIDER,
      "outputs.manifestNamesByNamespace": [
        (namespace): [
          replicaSet,
          deployment
        ]
      ],
    ]
    def replicaSetRefreshDetails = [
      account: ACCOUNT,
      location: namespace,
      name: replicaSet
    ]
    def deploymentRefreshDetails = [
      account: ACCOUNT,
      location: namespace,
      name: deployment
    ]
    def stage = mockStage(context)

    when:
    def taskResult = task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, replicaSetRefreshDetails) >> mockResponse(HTTP_ACCEPTED)
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, deploymentRefreshDetails) >> mockResponse(HTTP_ACCEPTED)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [
      processedRefresh(replicaSetRefreshDetails),
      pendingRefresh(deploymentRefreshDetails)
    ]
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [
      processedRefresh(replicaSetRefreshDetails),
      processedRefresh(deploymentRefreshDetails)
    ]
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.SUCCEEDED
  }

  def "returns RUNNING if there is an outstanding request, even if all requests in the current iteration succeeded"() {
    given:
    def namespace = "my-namespace"
    def replicaSet = "replicaSet my-replicaset-v014"
    def deployment = "deployment my-deployment"
    def context = [
      account: ACCOUNT,
      cloudProvider: PROVIDER,
      "outputs.manifestNamesByNamespace": [
        (namespace): [
          replicaSet,
          deployment
        ]
      ],
    ]
    def replicaSetRefreshDetails = [
      account: ACCOUNT,
      location: namespace,
      name: replicaSet
    ]
    def deploymentRefreshDetails = [
      account: ACCOUNT,
      location: namespace,
      name: deployment
    ]
    def stage = mockStage(context)

    when:
    def taskResult = task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, replicaSetRefreshDetails) >> mockResponse(HTTP_ACCEPTED)
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, deploymentRefreshDetails) >> mockResponse(HTTP_ACCEPTED)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [
      pendingRefresh(replicaSetRefreshDetails)
    ]
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, deploymentRefreshDetails) >> mockResponse(HTTP_OK)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [
      processedRefresh(replicaSetRefreshDetails)
    ]
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.SUCCEEDED
  }

  def "handles refreshing the cache for manifests in different namespaces"() {
    given:
    def replicaSetNamespace = "replicaSet-namespace"
    def deploymentNamespace = "deployment-namespace"
    def replicaSet = "replicaSet my-replicaset-v014"
    def deployment = "deployment my-deployment"
    def context = [
      account: ACCOUNT,
      cloudProvider: PROVIDER,
      "outputs.manifestNamesByNamespace": [
        (replicaSetNamespace): [
          replicaSet
        ],
        (deploymentNamespace): [
          deployment
        ]
      ],
    ]
    def replicaSetRefreshDetails = [
      account: ACCOUNT,
      location: replicaSetNamespace,
      name: replicaSet
    ]
    def deploymentRefreshDetails = [
      account: ACCOUNT,
      location: deploymentNamespace,
      name: deployment
    ]
    def stage = mockStage(context)

    when:
    def taskResult = task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, replicaSetRefreshDetails) >> mockResponse(HTTP_ACCEPTED)
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, deploymentRefreshDetails) >> mockResponse(HTTP_ACCEPTED)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [
      processedRefresh(replicaSetRefreshDetails),
      pendingRefresh(deploymentRefreshDetails)
    ]
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [
      processedRefresh(replicaSetRefreshDetails),
      processedRefresh(deploymentRefreshDetails)
    ]
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.SUCCEEDED
  }

  def "properly handles a manifest without a namespace"() {
    given:
    def namespace = ""
    def manifest = "namespace new-namespace"
    def context = [
      account: ACCOUNT,
      cloudProvider: PROVIDER,
      "outputs.manifestNamesByNamespace": [
        (namespace): [
          manifest
        ]
      ],
    ]
    def refreshDetails = [
      account: ACCOUNT,
      location: namespace,
      name: manifest
    ]
    def stage = mockStage(context)

    when:
    def taskResult = task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, refreshDetails) >> mockResponse(HTTP_ACCEPTED)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [pendingRefresh(refreshDetails)]
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [processedRefresh(refreshDetails)]
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.SUCCEEDED
  }

  def "properly handles a manifest without a namespace, even if incorrectly assigned a namespace"() {
    given:
    def namespace = "my-namespace"
    def manifest = "namespace new-namespace"
    def context = [
      account: ACCOUNT,
      cloudProvider: PROVIDER,
      "outputs.manifestNamesByNamespace": [
        (namespace): [
          manifest
        ]
      ],
    ]
    def refreshDetails = [
      account: ACCOUNT,
      location: namespace,
      name: manifest
    ]
    def refreshResponseDetails = [
      account: ACCOUNT,
      location: "",
      name: manifest
    ]
    def stage = mockStage(context)

    when:
    def taskResult = task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, refreshDetails) >> mockResponse(HTTP_ACCEPTED)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [pendingRefresh(refreshResponseDetails)]
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.RUNNING

    when:
    context << taskResult.context
    stage = mockStage(context)
    taskResult = task.execute(stage)

    then:
    1 * cacheStatusService.pendingForceCacheUpdates(PROVIDER, REFRESH_TYPE) >> [processedRefresh(refreshResponseDetails)]
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.SUCCEEDED
  }

  private Response mockResponse(int status) {
    return new Response("", status, "", [], null)
  }

  private Stage mockStage(Map context) {
    Stage stage = new Stage(new Execution(PIPELINE, "test"), "whatever", context)
    stage.setStartTime(now.toEpochMilli())
    return stage
  }

  private Map pendingRefresh(Map refreshDetails) {
    return [
      details: refreshDetails,
      processedCount: 0,
      processedTime: -1,
      cacheTime: now.plusMillis(10).toEpochMilli()
    ]
  }

  private Map processedRefresh(Map refreshDetails) {
    return [
      details: refreshDetails,
      processedCount: 1,
      processedTime: now.plusMillis(5000).toEpochMilli(),
      cacheTime: now.plusMillis(10).toEpochMilli()
    ]
  }

  private Map staleRefresh(Map refreshDetails) {
    return [
      details: refreshDetails,
      processedCount: 1,
      processedTime: now.plusMillis(5000).toEpochMilli(),
      cacheTime: now.minusMillis(10).toEpochMilli()
    ]
  }

  def "reads manifests from `manifestNamesByNamespaceToRefresh` key if available"() {
    given:
    def namespace = "my-namespace"
    def manifestA = "replicaSet my-replicaset-v014"
    def manifestB = "replicaSet my-replicaset-v015"
    def context = [
      account: ACCOUNT,
      cloudProvider: PROVIDER,
      "outputs.manifestNamesByNamespace": [
        (namespace): [
          manifestA
        ]
      ],
      "manifestNamesByNamespaceToRefresh": [
        (namespace): [
          manifestB
        ]
      ],
      "shouldRefreshManifestNamesByNamespaceToRefresh": true
    ]
    def refreshDetails = [
      account: ACCOUNT,
      location: namespace,
      name: manifestB
    ]
    def stage = mockStage(context)
    stage.setStartTime(now.toEpochMilli())

    when:
    def taskResult = task.execute(stage)

    then:
    1 * cacheService.forceCacheUpdate(PROVIDER, REFRESH_TYPE, refreshDetails) >> mockResponse(HTTP_OK)
    0 * cacheService._
    taskResult.getStatus() == ExecutionStatus.SUCCEEDED
  }
}
