/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipeline.persistence.jedis

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientSelector
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepositoryTck
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Unroll

import java.util.concurrent.CountDownLatch

import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.*
import static java.util.concurrent.TimeUnit.SECONDS

class JedisExecutionRepositorySpec extends ExecutionRepositoryTck<RedisExecutionRepository> {

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedisPrevious

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
    embeddedRedisPrevious = EmbeddedRedis.embed()
  }

  def cleanup() {
    embeddedRedis.jedis.withCloseable { it.flushDB() }
    embeddedRedisPrevious.jedis.withCloseable { it.flushDB() }
  }

  Pool<Jedis> jedisPool = embeddedRedis.pool
  Pool<Jedis> jedisPoolPrevious = embeddedRedisPrevious.pool

  RedisClientDelegate redisClientDelegate = new JedisClientDelegate("primaryDefault", jedisPool)
  RedisClientDelegate previousRedisClientDelegate = new JedisClientDelegate("previousDefault", jedisPoolPrevious)
  RedisClientSelector redisClientSelector = new RedisClientSelector([redisClientDelegate, previousRedisClientDelegate])

  Registry registry = new NoopRegistry()

  @AutoCleanup
  def jedis = jedisPool.resource

  @Override
  RedisExecutionRepository createExecutionRepository() {
    return new RedisExecutionRepository(registry, redisClientSelector, 1, 50)
  }

  @Override
  RedisExecutionRepository createExecutionRepositoryPrevious() {
    return new RedisExecutionRepository(registry, new RedisClientSelector([new JedisClientDelegate("primaryDefault", jedisPoolPrevious)]), 1, 50)
  }


  def "cleans up indexes of non-existent executions"() {
    given:
    jedis.sadd("allJobs:pipeline", id)

    when:
    def result = repository.retrieve(PIPELINE).toList().toBlocking().first()

    then:
    result.isEmpty()

    and:
    !jedis.sismember("allJobs:pipeline", id)

    where:
    id = "some-pipeline-id"
  }

  def "storing/deleting a pipeline updates the executionsByPipeline set"() {
    given:
    def pipeline = pipeline {
      stage {
        type = "one"
      }
      application = "someApp"
    }

    when:
    repository.store(pipeline)

    then:
    jedis.zrange(RedisExecutionRepository.executionsByPipelineKey(pipeline.pipelineConfigId), 0, 1) == [
      pipeline.id
    ] as Set<String>

    when:
    repository.delete(pipeline.type, pipeline.id)
    repository.retrieve(pipeline.type, pipeline.id)

    then:
    thrown ExecutionNotFoundException

    and:
    repository.retrieve(PIPELINE).toList().toBlocking().first() == []
    jedis.zrange(RedisExecutionRepository.executionsByPipelineKey(pipeline.pipelineConfigId), 0, 1).isEmpty()
  }

  @Unroll
  def "retrieving orchestrations limits the number of returned results"() {
    given:
    4.times {
      repository.store(orchestration {
        application = "orca"
        trigger = new DefaultTrigger("manual", "fnord")
      })
    }

    when:
    def retrieved = repository.retrieveOrchestrationsForApplication("orca", new ExecutionRepository.ExecutionCriteria(limit: limit))
      .toList().toBlocking().first()

    then:
    retrieved.size() == actual

    where:
    limit || actual
    0     || 0
    1     || 1
    2     || 2
    4     || 4
    100   || 4
  }

  def "can store an orchestration already in previousRedis back to previousRedis"() {
    given:
    def orchestration = orchestration {
      application = "paperclips"
      trigger = new DefaultTrigger("manual", "fnord")
      stage {
        type = "one"
        context = [:]
      }
    }

    when:
    previousRepository.store(orchestration)
    def retrieved = repository.retrieve(ORCHESTRATION, orchestration.id)

    then:
    retrieved.id == orchestration.id

    and:
    def stage = retrieved.stages.first()
    stage.setContext([this: "works"])
    repository.store(retrieved)

    then:
    previousRepository.retrieve(ORCHESTRATION, orchestration.id).stages.first().getContext() == [this: "works"]
  }

  def "can updateStageContext against previousRedis"() {
    given:
    def orchestration = orchestration {
      application = "paperclips"
      trigger = new DefaultTrigger("manual", "fnord")
      stage {
        type = "one"
        context = [:]
      }
    }

    when:
    previousRepository.store(orchestration)
    def retrieved = repository.retrieve(ORCHESTRATION, orchestration.id)

    then:
    retrieved.id == orchestration.id

    and:
    def stage = retrieved.stages.first()
    stage.setContext([why: 'hello'])
    repository.updateStageContext(stage)

    then:
    repository.retrieve(ORCHESTRATION, orchestration.id).stages.first().getContext() == [why: 'hello']

  }

  def "can retrieve running orchestration in previousRedis by correlation id"() {
    given:
    def execution = orchestration {
      trigger = new DefaultTrigger("manual", "covfefe")
    }
    previousRepository.store(execution)
    previousRepository.updateStatus(execution.type, execution.id, RUNNING)

    when:
    def result = repository.retrieveOrchestrationForCorrelationId('covfefe')

    then:
    result.id == execution.id

    when:
    repository.updateStatus(execution.type, execution.id, SUCCEEDED)
    repository.retrieveOrchestrationForCorrelationId('covfefe')

    then:
    thrown(ExecutionNotFoundException)
  }

  def "can retrieve orchestrations from multiple redis stores"() {
    given:
    3.times {
      repository.store(orchestration {
        application = "orca"
        trigger = new DefaultTrigger("manual", "fnord")
      })
    }

    and:
    3.times {
      previousRepository.store(orchestration {
        application = "orca"
        trigger = new DefaultTrigger("manual", "fnord")
      })
    }

    when:
    // TODO-AJ limits are current applied to each backing redis
    def retrieved = repository.retrieveOrchestrationsForApplication("orca", new ExecutionRepository.ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    // orchestrations are stored in an unsorted set and results are non-deterministic
    retrieved.size() == 4
  }

  def "can delete orchestrations from multiple redis stores"() {
    given:
    def orchestration1 = orchestration {
      application = "orca"
      trigger = new DefaultTrigger("manual", "fnord")
    }
    repository.store(orchestration1)

    and:
    def orchestration2 = orchestration {
      application = "orca"
      trigger = new DefaultTrigger("manual", "fnord")
    }
    previousRepository.store(orchestration2)

    when:
    repository.delete(orchestration1.type, orchestration1.id)
    def retrieved = repository.retrieveOrchestrationsForApplication("orca", new ExecutionRepository.ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    retrieved*.id == [orchestration2.id]

    when:
    repository.delete(orchestration2.type, orchestration2.id)
    retrieved = repository.retrieveOrchestrationsForApplication("orca", new ExecutionRepository.ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    retrieved.isEmpty()
  }

  def "can retrieve pipelines using pipelineConfigIds and buildTime boundaries"() {
    given:
    repository.store(pipeline {
      application = "app-1"
      pipelineConfigId = "pipeline-1"
      buildTime = 10
    })
    repository.store(pipeline {
      application = "app-2"
      pipelineConfigId = "pipeline-2"
      buildTime = 11
    })
    repository.store(pipeline {
      application = "app-3"
      pipelineConfigId = "pipeline-3"
      buildTime = 12
    })

    and:
    previousRepository.store(pipeline {
      application = "app-1"
      pipelineConfigId = "pipeline-1"
      buildTime = 7
    })
    previousRepository.store(pipeline {
      application = "app-2"
      pipelineConfigId = "pipeline-2"
      buildTime = 8
    })
    previousRepository.store(pipeline {
      application = "app-3"
      pipelineConfigId = "pipeline-3"
      buildTime = 9
    })

    when:
    def retrieved = repository.retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
      Arrays.asList("pipeline-2", "pipeline-3"),
      9L,
      11L,
      10
    )
      .sorted({ a, b -> a.buildTime <=> b.buildTime })
      .toList().toBlocking().first()

    then:
    retrieved*.buildTime == [9L, 11L]
  }

  def "can retrieve pipeline without stageIndex"() {
    given:
    def pipeline = pipeline {
      application = "orca"
      name = "dummy-pipeline"
      stage { type = "one" }
      stage { type = "two" }
      stage { type = "three" }
    }

    repository.store(pipeline)

    when:
    jedis.exists("pipeline:${pipeline.id}:stageIndex")

    and:
    jedis.del("pipeline:${pipeline.id}:stageIndex")
    def retrieved = repository.retrieve(PIPELINE, pipeline.id)

    then:
    retrieved.stages.size() == 3
    retrieved.stages*.id.sort() == pipeline.stages*.id.sort()
  }

  def "can retrieve pipelines from multiple redis stores"() {
    given:
    repository.store(pipeline {
      application = "orca"
      pipelineConfigId = "pipeline-1"
      buildTime = 10
    })
    repository.store(pipeline {
      application = "orca"
      pipelineConfigId = "pipeline-1"
      buildTime = 11
    })
    repository.store(pipeline {
      application = "orca"
      pipelineConfigId = "pipeline-1"
      buildTime = 12
    })

    and:
    previousRepository.store(pipeline {
      application = "orca"
      pipelineConfigId = "pipeline-1"
      buildTime = 7
    })
    previousRepository.store(pipeline {
      application = "orca"
      pipelineConfigId = "pipeline-1"
      buildTime = 8
    })
    previousRepository.store(pipeline {
      application = "orca"
      pipelineConfigId = "pipeline-1"
      buildTime = 9
    })

    when:
    // TODO-AJ limits are current applied to each backing redis
    def retrieved = repository.retrievePipelinesForPipelineConfigId("pipeline-1", new ExecutionRepository.ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    // pipelines are stored in a sorted sets and results should be reverse buildTime ordered
    retrieved*.buildTime == [12L, 11L, 9L, 8L]
  }

  def "can delete pipelines from multiple redis stores"() {
    given:
    def pipeline1 = pipeline {
      application = "orca"
      pipelineConfigId = "pipeline-1"
      buildTime = 11
    }
    repository.store(pipeline1)

    and:
    def pipeline2 = pipeline {
      application = "orca"
      pipelineConfigId = "pipeline-1"
      buildTime = 10
    }
    previousRepository.store(pipeline2)

    when:
    repository.delete(pipeline1.type, pipeline1.id)
    def retrieved = repository.retrievePipelinesForPipelineConfigId("pipeline-1", new ExecutionRepository.ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    retrieved*.id == [pipeline2.id]

    when:
    repository.delete(pipeline2.type, pipeline2.id)
    retrieved = repository.retrievePipelinesForPipelineConfigId("pipeline-1", new ExecutionRepository.ExecutionCriteria(limit: 2))
      .toList().toBlocking().first()

    then:
    retrieved.isEmpty()
  }

  def "should remove null 'stage' keys"() {
    given:
    def pipeline = pipeline {
      application = "orca"
      name = "dummy-pipeline"
      stage {
        type = "one"
        context = [foo: "foo"]
        startTime = 100
        endTime = 200
      }
    }
    repository.store(pipeline)

    when:
    def fetchedPipeline = repository.retrieve(pipeline.type, pipeline.id)

    then:
    fetchedPipeline.stages[0].startTime == 100
    fetchedPipeline.stages[0].endTime == 200

    when:
    fetchedPipeline.stages[0].startTime = null
    fetchedPipeline.stages[0].endTime = null
    repository.storeStage(fetchedPipeline.stages[0])

    fetchedPipeline = repository.retrieve(pipeline.type, pipeline.id)

    then:
    fetchedPipeline.stages[0].startTime == null
    fetchedPipeline.stages[0].endTime == null
  }

  def "can remove a stage leaving other stages unaffected"() {
    given:
    def pipeline = pipeline {
      application = "orca"
      name = "dummy-pipeline"
      stage {
        type = "one"
        refId = "1"
      }
      stage {
        type = "two"
        refId = "2"
      }
      stage {
        type = "three"
        refId = "3"
      }
    }

    repository.store(pipeline)

    expect:
    repository.retrieve(pipeline.type, pipeline.id).stages.size() == 3

    when:
    repository.removeStage(pipeline, pipeline.namedStage("two").id)

    then:
    with(repository.retrieve(pipeline.type, pipeline.id)) {
      stages.size() == 2
      stages.type == ["one", "three"]
    }
  }

  @Unroll
  def "can add a synthetic stage #position"() {
    given:
    def pipeline = pipeline {
      application = "orca"
      name = "dummy-pipeline"
      stage {
        type = "one"
        refId = "1"
      }
      stage {
        type = "two"
        refId = "2"
      }
      stage {
        type = "three"
        refId = "3"
      }
    }

    repository.store(pipeline)

    expect:
    repository.retrieve(pipeline.type, pipeline.id).stages.size() == 3

    when:
    def stage = newStage(pipeline, "whatever", "two-whatever", [:], pipeline.namedStage("two"), position)
    stage.setRefId(newRefId)
    repository.addStage(stage)

    then:
    with(repository.retrieve(pipeline.type, pipeline.id)) {
      stages.size() == 4
      stages.name == expectedStageNames
    }

    where:
    position     | expectedStageNames                      | newRefId
    STAGE_BEFORE | ["one", "two-whatever", "two", "three"] | "2<1"
    STAGE_AFTER  | ["one", "two", "two-whatever", "three"] | "2>1"
  }

  def "can concurrently add stages without overwriting"() {
    given:
    def pipeline = pipeline {
      application = "orca"
      name = "dummy-pipeline"
      stage {
        type = "one"
        refId = "1"
      }
      stage {
        type = "two"
        refId = "2"
      }
      stage {
        type = "three"
        refId = "3"
      }
    }

    repository.store(pipeline)

    expect:
    repository.retrieve(pipeline.type, pipeline.id).stages.size() == 3

    when:
    def stage1 = newStage(pipeline, "whatever", "one-whatever", [:], pipeline.namedStage("one"), STAGE_BEFORE)
    stage1.setRefId("1<1")
    def stage2 = newStage(pipeline, "whatever", "two-whatever", [:], pipeline.namedStage("two"), STAGE_BEFORE)
    stage2.setRefId("2<1")
    def stage3 = newStage(pipeline, "whatever", "three-whatever", [:], pipeline.namedStage("three"), STAGE_BEFORE)
    stage3.setRefId("3<1")
    def startLatch = new CountDownLatch(1)
    def doneLatch = new CountDownLatch(3)
    [stage1, stage2, stage3].each { stage ->
      Thread.start {
        startLatch.await(1, SECONDS)
        repository.addStage(stage)
        doneLatch.countDown()
      }
    }
    startLatch.countDown()

    then:
    doneLatch.await(1, SECONDS)

    and:
    with(repository.retrieve(pipeline.type, pipeline.id)) {
      stages.size() == 6
      stages.name == ["one-whatever", "one", "two-whatever", "two", "three-whatever", "three"]
    }
  }

  def "can save a stage with all data and update stage context"() {
    given:
    def pipeline = pipeline {
      application = "orca"
      name = "dummy-pipeline"
      stage { type = "one" }
    }

    repository.store(pipeline)

    def stage = newStage(pipeline, "whatever", "one-whatever", [:], pipeline.namedStage("one"), STAGE_BEFORE)
    stage.lastModified = new Stage.LastModifiedDetails(user: "rfletcher@netflix.com", allowedAccounts: ["whatever"], lastModifiedTime: System.currentTimeMillis())
    stage.startTime = System.currentTimeMillis()
    stage.endTime = System.currentTimeMillis()
    stage.refId = "1<1"

    when:
    repository.addStage(stage)

    then:
    notThrown(Exception)

    when:
    stage.setContext([foo: 'bar'])
    repository.updateStageContext(stage)

    then:
    def stored = repository.retrieve(PIPELINE, pipeline.id)

    and:
    stored.stageById(stage.id).context == [foo: 'bar']
  }

  def "can deserialize ancient executions"() {
    given:
    Jedis jedis = embeddedRedis.getJedis()
    def key = repository.pipelineKey("ancient")

    jedis.hmset(key, [
      "canceledBy"                                                                                 : "parent pipeline",
      "parallel"                                                                                   : "true",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-1-Deployinuseast1.initializationStage"           : "false",
      "stage.adeba849-eb19-4b25-be36-99458496b37e.context"                                         : "{\"clusters\":[{\"account\":\"test\",\"healthCheckType\":\"EC2\",\"cooldown\":10,\"securityGroups\":[\"sg-xxx\",\"sg-xxx\",\"xxx\"],\"targetHealthyDeployPercentage\":100,\"instanceType\":\"m3.xlarge\",\"capacity\":{\"desired\":3,\"min\":3,\"max\":3},\"provider\":\"aws\",\"keyPair\":\"xxx\",\"loadBalancers\":[\"xxx\"],\"freeFormDetails\":\"\",\"useSourceCapacity\":false,\"instanceMonitoring\":true,\"stack\":\"prod\",\"availabilityZones\":{\"us-east-1\":[\"us-east-1c\",\"us-east-1d\",\"us-east-1e\"]},\"application\":\"xxx\",\"terminationPolicies\":[\"Default\"],\"strategy\":\"redblack\",\"iamRole\":\"xxx\",\"subnetType\":\"internal (vpc0)\",\"tags\":{},\"suspendedProcesses\":[],\"ebsOptimized\":false,\"healthCheckGracePeriod\":600,\"cloudProvider\":\"aws\"}],\"stageDetails\":{\"name\":\"Deploy\",\"type\":\"deploy\",\"startTime\":1489511660054,\"isSynthetic\":false},\"batch.task.id.beginParallel\":206726}",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44.tasks"                                           : "[{\"id\":\"1\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.DisableServerGroupTask\",\"name\":\"disableServerGroup\",\"status\":\"NOT_STARTED\",\"stageStart\":true,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"2\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask\",\"name\":\"monitorServerGroup\",\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"3\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForAllInstancesNotUpTask\",\"name\":\"waitForNotUpInstances\",\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"4\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask\",\"name\":\"forceCacheRefresh\",\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"5\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.DestroyServerGroupTask\",\"name\":\"destroyServerGroup\",\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"6\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask\",\"name\":\"monitorServerGroup\",\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"7\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForDestroyedServerGroupTask\",\"name\":\"waitForDestroyedServerGroup\",\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":true,\"loopStart\":false,\"loopEnd\":false}]",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44-1-determineTargetServerGroup.status"             : "NOT_STARTED",
      "notifications"                                                                              : "[]",
      "stage.81aa7749-1756-418f-8bcf-7d26a2d6c13b.tasks"                                           : "[{\"id\":\"1\",\"implementingClass\":\"com.netflix.spinnaker.orca.pipeline.tasks.WaitTask\",\"name\":\"wait\",\"status\":\"NOT_STARTED\",\"stageStart\":true,\"stageEnd\":true,\"loopStart\":false,\"loopEnd\":false}]",
      "context"                                                                                    : "{\"deploymentDetails\":[{\"ami\":\"ami-xxx\",\"imageId\":\"ami-xxx\",\"imageName\":\"xxx\",\"cloudProvider\":\"aws\",\"refId\":\"0\",\"sourceServerGroup\":\"xxx\",\"region\":\"us-east-1\",\"kernelId\":\"aki-xxx\",\"virtualizationType\":\"paravirtual\",\"blockDeviceMappings\":[{\"deviceName\":\"/dev/sda1\",\"ebs\":{\"snapshotId\":\"snap-xxx\",\"volumeSize\":8,\"deleteOnTermination\":true,\"volumeType\":\"standard\",\"encrypted\":false}},{\"virtualName\":\"ephemeral0\",\"deviceName\":\"/dev/sdb\"},{\"virtualName\":\"ephemeral1\",\"deviceName\":\"/dev/sdc\"},{\"virtualName\":\"ephemeral2\",\"deviceName\":\"/dev/sdd\"},{\"virtualName\":\"ephemeral3\",\"deviceName\":\"/dev/sde\"}],\"description\":\"name=xxx, arch=x86_64, ancestor_name=xxx, ancestor_id=ami-xxx, ancestor_version=xxx\",\"ownerId\":\"xxx\",\"creationDate\":\"2017-03-14T16:21:07.000Z\",\"imageLocation\":\"xxx/xxx\",\"rootDeviceType\":\"ebs\",\"tags\":[{\"key\":\"creator\",\"value\":\"xxx\"},{\"key\":\"creation_time\",\"value\":\"2017-03-14 16:21:12 UTC\"},{\"key\":\"appversion\",\"value\":\"xxx\"},{\"key\":\"base_ami_version\",\"value\":\"xxx\"},{\"key\":\"build_host\",\"value\":\"xxx\"}],\"public\":false,\"hypervisor\":\"xen\",\"name\":\"xxx\",\"rootDeviceName\":\"/dev/sda1\",\"state\":\"available\",\"productCodes\":[],\"imageType\":\"machine\",\"architecture\":\"x86_64\",\"package_name\":\"xxx\",\"version\":\"1.944\",\"commit\":\"11ddd09\",\"jenkins\":{\"name\":\"xxx\",\"number\":\"1098\",\"host\":\"xxx\"}}]}",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44.context"                                         : "{\"cluster\":\"xxx\",\"cloudProviderType\":\"aws\",\"credentials\":\"test\",\"target\":\"ancestor_asg_dynamic\",\"cloudProvider\":\"aws\",\"region\":\"us-east-1\",\"targetLocation\":{\"type\":\"REGION\",\"value\":\"us-east-1\"}}",
      "stage.adeba849-eb19-4b25-be36-99458496b37e.startTime"                                       : "1489511660067",
      "stage.adeba849-eb19-4b25-be36-99458496b37e.type"                                            : "deploy",
      "name"                                                                                       : "Deploy to Prod",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-1-Deployinuseast1.scheduledTime"                 : "0",
      "limitConcurrent"                                                                            : "false",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-1-Deployinuseast1.endTime"                       : "1489511671237",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44-1-determineTargetServerGroup.name"               : "determineTargetServerGroup",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44.requisiteStageRefIds"                            : "2",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-1-Deployinuseast1.startTime"                     : "1489511660121",
      "application"                                                                                : "xxx",
      "stage.adeba849-eb19-4b25-be36-99458496b37e.refId"                                           : "1",
      "stage.fc904037-a1ab-412c-bbdf-e8359afd60ea.initializationStage"                             : "false",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-3-disableCluster.status"                         : "NOT_STARTED",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-3-disableCluster.tasks"                          : "[{\"id\":\"1\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask\",\"name\":\"determineHealthProviders\",\"status\":\"NOT_STARTED\",\"stageStart\":true,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"2\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.cluster.DisableClusterTask\",\"name\":\"disableCluster\",\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"3\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask\",\"name\":\"monitorDisableCluster\",\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"4\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask\",\"name\":\"forceCacheRefresh\",\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"5\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.cluster.WaitForClusterDisableTask\",\"name\":\"waitForClusterDisable\",\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"6\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask\",\"name\":\"forceCacheRefresh\",\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":true,\"loopStart\":false,\"loopEnd\":false}]",
      "appConfig"                                                                                  : "{}",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-1-Deployinuseast1.syntheticStageOwner"           : "STAGE_AFTER",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44.scheduledTime"                                   : "0",
      "canceled"                                                                                   : "true",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-3-disableCluster.scheduledTime"                  : "0",
      "stage.adeba849-eb19-4b25-be36-99458496b37e.scheduledTime"                                   : "0",
      "stage.fc904037-a1ab-412c-bbdf-e8359afd60ea.name"                                            : "Find AMI",
      "status"                                                                                     : "CANCELED",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-1-Deployinuseast1.type"                          : "deploy",
      "stage.fc904037-a1ab-412c-bbdf-e8359afd60ea.requisiteStageRefIds"                            : "",
      "initialConfig"                                                                              : "{}",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-3-disableCluster.type"                           : "disableCluster",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44.type"                                            : "destroyServerGroup",
      "authentication"                                                                             : "{\"user\":\"xxx\",\"allowedAccounts\":[\"xxx\"]}",
      "stage.81aa7749-1756-418f-8bcf-7d26a2d6c13b.refId"                                           : "2",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-3-disableCluster.context"                        : "{\"region\":\"us-east-1\",\"cluster\":\"xxx\",\"credentials\":\"test\",\"cloudProvider\":\"aws\",\"remainingEnabledServerGroups\":1,\"preferLargerOverNewer\":false}",
      "stage.adeba849-eb19-4b25-be36-99458496b37e.status"                                          : "RUNNING",
      "stage.adeba849-eb19-4b25-be36-99458496b37e.name"                                            : "Deploy",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-1-Deployinuseast1.parentStageId"                 : "adeba849-eb19-4b25-be36-99458496b37e",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-1-Deployinuseast1.context"                       : "{\"healthCheckType\":\"EC2\",\"stack\":\"prod\",\"terminationPolicies\":[\"Default\"],\"targetHealthyDeployPercentage\":100,\"availabilityZones\":{\"us-east-1\":[\"us-east-1c\",\"us-east-1d\",\"us-east-1e\"]},\"type\":\"createServerGroup\",\"capacity\":{\"desired\":3,\"min\":3,\"max\":3},\"freeFormDetails\":\"\",\"provider\":\"aws\",\"healthCheckGracePeriod\":600,\"cloudProvider\":\"aws\",\"cooldown\":10,\"ebsOptimized\":false,\"instanceType\":\"m3.xlarge\",\"loadBalancers\":[\"xxx\"],\"useSourceCapacity\":false,\"tags\":{},\"iamRole\":\"xxx\",\"application\":\"xxx\",\"name\":\"Deploy in us-east-1\",\"securityGroups\":[\"sg-xxx\",\"sg-xxx\",\"xxx\"],\"keyPair\":\"xxx\",\"strategy\":\"redblack\",\"suspendedProcesses\":[],\"instanceMonitoring\":true,\"account\":\"test\",\"subnetType\":\"internal (vpc0)\",\"stageDetails\":{\"name\":\"Deploy in us-east-1\",\"type\":\"deploy\",\"startTime\":1489511660109,\"isSynthetic\":true},\"batch.task.id.determineSourceServerGroup\":206728,\"source\":{\"asgName\":\"xxx\",\"serverGroupName\":\"xxx\",\"account\":\"test\",\"region\":\"us-east-1\",\"useSourceCapacity\":false},\"batch.task.id.determineHealthProviders\":206733,\"notification.type\":\"createdeploy\",\"deploy.account.name\":\"test\",\"kato.last.task.id\":{\"id\":\"249459\"},\"batch.task.id.createServerGroup\":206736,\"kato.result.expected\":true,\"kato.tasks\":[{\"id\":\"249459\",\"status\":{\"completed\":false,\"failed\":false},\"history\":[{\"phase\":\"ORCHESTRATION\",\"status\":\"Initializing Orchestration Task...\"},{\"phase\":\"ORCHESTRATION\",\"status\":\"Processing op: DeployAtomicOperation\"},{\"phase\":\"DEPLOY\",\"status\":\"Initializing phase.\"},{\"phase\":\"DEPLOY\",\"status\":\"Looking for BasicAmazonDeployDescription handler...\"},{\"phase\":\"DEPLOY\",\"status\":\"Found handler: BasicAmazonDeployHandler\"},{\"phase\":\"DEPLOY\",\"status\":\"Invoking Handler.\"},{\"phase\":\"DEPLOY\",\"status\":\"Initializing handler...\"},{\"phase\":\"DEPLOY\",\"status\":\"Preparing deployment to [us-east-1:[us-east-1c, us-east-1d, us-east-1e]]...\"}],\"resultObjects\":[]}],\"cancelResults\":[]}",
      "executionEngine"                                                                            : "v2",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44.status"                                          : "NOT_STARTED",
      "stage.adeba849-eb19-4b25-be36-99458496b37e.tasks"                                           : "[{\"id\":\"1\",\"implementingClass\":\"com.netflix.spinnaker.orca.pipeline.tasks.NoOpTask\",\"name\":\"beginParallel\",\"startTime\":1489511660069,\"endTime\":1489511660084,\"status\":\"SUCCEEDED\",\"stageStart\":true,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"2\",\"implementingClass\":\"com.netflix.spinnaker.orca.kato.pipeline.ParallelDeployStage\$CompleteParallelDeployTask\",\"name\":\"completeParallelDeploy\",\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":true,\"loopStart\":false,\"loopEnd\":false}]",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44-1-determineTargetServerGroup.tasks"              : "[{\"id\":\"1\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.support.DetermineTargetServerGroupTask\",\"name\":\"determineTargetServerGroup\",\"status\":\"NOT_STARTED\",\"stageStart\":true,\"stageEnd\":true,\"loopStart\":false,\"loopEnd\":false}]",
      "buildTime"                                                                                  : "1489511659199",
      "stage.fc904037-a1ab-412c-bbdf-e8359afd60ea.refId"                                           : "0",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-3-disableCluster.parentStageId"                  : "adeba849-eb19-4b25-be36-99458496b37e-1-Deployinuseast1",
      "trigger"                                                                                    : "{\"type\":\"pipeline\",\"user\":\"xxx\",\"parentPipelineId\":\"021a91c5-3837-4038-a0a2-fde2116f3683\",\"parentPipelineApplication\":\"xxx\",\"parentStatus\":\"RUNNING\",\"parentExecution\":{\"id\":\"021a91c5-3837-4038-a0a2-fde2116f3683\",\"application\":\"xxx\",\"executingInstance\":\"i-xxx\",\"executionEngine\":\"v2\",\"buildTime\":1489511659036,\"canceled\":false,\"canceledBy\":null,\"cancellationReason\":null,\"parallel\":true,\"limitConcurrent\":true,\"keepWaitingPipelines\":false,\"appConfig\":{},\"context\":{},\"stages\":[{\"id\":\"f8e7aff7-0c1f-433d-adfb-50063b99e686\",\"type\":\"deploy\",\"name\":\"Deploy xxx Master to PROD\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"context\":{\"clusters\":[{\"application\":\"xxx\",\"strategy\":\"redblack\",\"capacity\":{\"desired\":1,\"min\":1,\"max\":1},\"targetHealthyDeployPercentage\":100,\"cooldown\":10,\"healthCheckType\":\"EC2\",\"healthCheckGracePeriod\":600,\"instanceMonitoring\":true,\"ebsOptimized\":false,\"iamRole\":\"xxx\",\"terminationPolicies\":[\"Default\"],\"availabilityZones\":{\"us-east-1\":[\"us-east-1c\",\"us-east-1d\",\"us-east-1e\"]},\"keyPair\":\"xxx\",\"suspendedProcesses\":[],\"securityGroups\":[\"sg-xxx\",\"sg-xxx\",\"sg-xxx\"],\"tags\":{},\"useAmiBlockDeviceMappings\":false,\"account\":\"test\",\"instanceType\":\"m3.xlarge\",\"provider\":\"aws\",\"loadBalancers\":[\"xxx\"],\"freeFormDetails\":\"\",\"useSourceCapacity\":false,\"stack\":\"prod\",\"subnetType\":\"internal (vpc0)\",\"cloudProvider\":\"aws\"}]},\"immutable\":false,\"initializationStage\":true,\"tasks\":[{\"id\":\"1\",\"implementingClass\":\"com.netflix.spinnaker.orca.pipeline.tasks.NoOpTask\",\"name\":\"beginParallel\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":true,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"2\",\"implementingClass\":\"com.netflix.spinnaker.orca.kato.pipeline.ParallelDeployStage\$CompleteParallelDeployTask\",\"name\":\"completeParallelDeploy\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":true,\"loopStart\":false,\"loopEnd\":false}],\"parentStageId\":null,\"refId\":\"2\",\"requisiteStageRefIds\":[\"6\"],\"syntheticStageOwner\":null,\"scheduledTime\":0,\"lastModified\":null},{\"id\":\"f8e7aff7-0c1f-433d-adfb-50063b99e686-1-Deployinuseast1\",\"type\":\"deploy\",\"name\":\"Deploy in us-east-1\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"context\":{\"healthCheckType\":\"EC2\",\"stack\":\"prod\",\"terminationPolicies\":[\"Default\"],\"targetHealthyDeployPercentage\":100,\"availabilityZones\":{\"us-east-1\":[\"us-east-1c\",\"us-east-1d\",\"us-east-1e\"]},\"type\":\"createServerGroup\",\"capacity\":{\"desired\":1,\"min\":1,\"max\":1},\"freeFormDetails\":\"\",\"provider\":\"aws\",\"healthCheckGracePeriod\":600,\"cloudProvider\":\"aws\",\"cooldown\":10,\"useAmiBlockDeviceMappings\":false,\"ebsOptimized\":false,\"instanceType\":\"m3.xlarge\",\"loadBalancers\":[\"xxx\"],\"useSourceCapacity\":false,\"tags\":{},\"iamRole\":\"xxx\",\"application\":\"xxx\",\"name\":\"Deploy in us-east-1\",\"keyPair\":\"xxx\",\"securityGroups\":[\"sg-xxx\",\"sg-xxx\",\"sg-xxx\"],\"strategy\":\"redblack\",\"suspendedProcesses\":[],\"instanceMonitoring\":true,\"account\":\"test\",\"subnetType\":\"internal (vpc0)\"},\"immutable\":false,\"initializationStage\":false,\"tasks\":[{\"id\":\"1\",\"implementingClass\":\"com.netflix.spinnaker.orca.kato.pipeline.strategy.DetermineSourceServerGroupTask\",\"name\":\"determineSourceServerGroup\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":true,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"2\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask\",\"name\":\"determineHealthProviders\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"3\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.CreateServerGroupTask\",\"name\":\"createServerGroup\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"4\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask\",\"name\":\"monitorDeploy\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"5\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask\",\"name\":\"forceCacheRefresh\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"6\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupMetadataTagTask\",\"name\":\"tagServerGroup\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"7\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstancesTask\",\"name\":\"waitForUpInstances\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"8\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask\",\"name\":\"forceCacheRefresh\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"9\",\"implementingClass\":\"com.netflix.spinnaker.orca.kato.tasks.JarDiffsTask\",\"name\":\"jarDiffs\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"10\",\"implementingClass\":\"com.netflix.spinnaker.orca.igor.tasks.GetCommitsTask\",\"name\":\"getCommits\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":true,\"loopStart\":false,\"loopEnd\":false}],\"parentStageId\":\"f8e7aff7-0c1f-433d-adfb-50063b99e686\",\"refId\":null,\"requisiteStageRefIds\":null,\"syntheticStageOwner\":\"STAGE_AFTER\",\"scheduledTime\":0,\"lastModified\":null},{\"id\":\"f8e7aff7-0c1f-433d-adfb-50063b99e686-3-disableCluster\",\"type\":\"disableCluster\",\"name\":\"disableCluster\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"context\":{\"region\":\"us-east-1\",\"cluster\":\"xxx\",\"credentials\":\"test\",\"cloudProvider\":\"aws\",\"remainingEnabledServerGroups\":1,\"preferLargerOverNewer\":false},\"immutable\":false,\"initializationStage\":false,\"tasks\":[{\"id\":\"1\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask\",\"name\":\"determineHealthProviders\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":true,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"2\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.cluster.DisableClusterTask\",\"name\":\"disableCluster\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"3\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask\",\"name\":\"monitorDisableCluster\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"4\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask\",\"name\":\"forceCacheRefresh\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"5\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.cluster.WaitForClusterDisableTask\",\"name\":\"waitForClusterDisable\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"6\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask\",\"name\":\"forceCacheRefresh\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":true,\"loopStart\":false,\"loopEnd\":false}],\"parentStageId\":\"f8e7aff7-0c1f-433d-adfb-50063b99e686-1-Deployinuseast1\",\"refId\":null,\"requisiteStageRefIds\":null,\"syntheticStageOwner\":\"STAGE_AFTER\",\"scheduledTime\":0,\"lastModified\":null},{\"id\":\"1d13f86d-2558-4aa7-babb-ee8de88440bc\",\"type\":\"pipeline\",\"name\":\"Trigger xxx Slave Deploy to Prod\",\"startTime\":1489511659167,\"endTime\":null,\"status\":\"RUNNING\",\"context\":{\"application\":\"xxx\",\"pipeline\":\"f9a9e5a0-3bc7-11e5-89a8-65c9c7540d0f\",\"failPipeline\":true,\"stageDetails\":{\"name\":\"Trigger xxx Slave Deploy to Prod\",\"type\":\"pipeline\",\"startTime\":1489511659149,\"isSynthetic\":false}},\"immutable\":false,\"initializationStage\":false,\"tasks\":[{\"id\":\"1\",\"implementingClass\":\"com.netflix.spinnaker.orca.front50.tasks.StartPipelineTask\",\"name\":\"startPipeline\",\"startTime\":1489511659169,\"endTime\":null,\"status\":\"RUNNING\",\"stageStart\":true,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"2\",\"implementingClass\":\"com.netflix.spinnaker.orca.front50.tasks.MonitorPipelineTask\",\"name\":\"monitorPipeline\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":true,\"loopStart\":false,\"loopEnd\":false}],\"parentStageId\":null,\"refId\":\"3\",\"requisiteStageRefIds\":[],\"syntheticStageOwner\":null,\"scheduledTime\":0,\"lastModified\":null},{\"id\":\"f1c5bd4d-8b14-4cde-a8da-ce00dca149b6\",\"type\":\"pipeline\",\"name\":\"Trigger xxx Index Deploy to Prod\",\"startTime\":1489511659163,\"endTime\":null,\"status\":\"RUNNING\",\"context\":{\"application\":\"xxx\",\"pipeline\":\"e6f522d0-ff7c-11e4-a8b3-f5bd0633341b\",\"failPipeline\":true,\"stageDetails\":{\"name\":\"Trigger xxx Index Deploy to Prod\",\"type\":\"pipeline\",\"startTime\":1489511659149,\"isSynthetic\":false}},\"immutable\":false,\"initializationStage\":false,\"tasks\":[{\"id\":\"1\",\"implementingClass\":\"com.netflix.spinnaker.orca.front50.tasks.StartPipelineTask\",\"name\":\"startPipeline\",\"startTime\":1489511659165,\"endTime\":null,\"status\":\"RUNNING\",\"stageStart\":true,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"2\",\"implementingClass\":\"com.netflix.spinnaker.orca.front50.tasks.MonitorPipelineTask\",\"name\":\"monitorPipeline\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":true,\"loopStart\":false,\"loopEnd\":false}],\"parentStageId\":null,\"refId\":\"4\",\"requisiteStageRefIds\":[],\"syntheticStageOwner\":null,\"scheduledTime\":0,\"lastModified\":null},{\"id\":\"b21843cc-48e4-43b4-8b18-47c916927652\",\"type\":\"findImage\",\"name\":\"Find AMI from Test\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"context\":{\"cluster\":\"xxx\",\"onlyEnabled\":true,\"regions\":[\"us-east-1\"],\"cloudProviderType\":\"aws\",\"credentials\":\"test\",\"selectionStrategy\":\"NEWEST\",\"cloudProvider\":\"aws\",\"stageDetails\":{\"name\":\"Find AMI from Test\",\"type\":\"findImage\",\"startTime\":1489511659149,\"isSynthetic\":false}},\"immutable\":false,\"initializationStage\":false,\"tasks\":[{\"id\":\"1\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.cluster.FindImageFromClusterTask\",\"name\":\"findImage\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":true,\"stageEnd\":true,\"loopStart\":false,\"loopEnd\":false}],\"parentStageId\":null,\"refId\":\"6\",\"requisiteStageRefIds\":[],\"syntheticStageOwner\":null,\"scheduledTime\":0,\"lastModified\":null},{\"id\":\"884d43bb-6cdd-4138-8bee-04b23f69f42c\",\"type\":\"wait\",\"name\":\"Wait in case of needed rollback\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"context\":{\"waitTime\":1800,\"comments\":\"Wait 30 min in case the push causes issues for some reason and we need to roll back\"},\"immutable\":false,\"initializationStage\":false,\"tasks\":[{\"id\":\"1\",\"implementingClass\":\"com.netflix.spinnaker.orca.pipeline.tasks.WaitTask\",\"name\":\"wait\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":true,\"stageEnd\":true,\"loopStart\":false,\"loopEnd\":false}],\"parentStageId\":null,\"refId\":\"7\",\"requisiteStageRefIds\":[\"2\"],\"syntheticStageOwner\":null,\"scheduledTime\":0,\"lastModified\":null},{\"id\":\"c3a57f13-c10d-4a90-ad41-1cf58afd42fa-1-determineTargetServerGroup\",\"type\":\"determineTargetServerGroup\",\"name\":\"determineTargetServerGroup\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"context\":{\"cluster\":\"xxx\",\"cloudProviderType\":\"aws\",\"regions\":[\"us-east-1\"],\"credentials\":\"test\",\"cloudProvider\":\"aws\",\"target\":\"oldest_asg_dynamic\"},\"immutable\":false,\"initializationStage\":false,\"tasks\":[{\"id\":\"1\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.support.DetermineTargetServerGroupTask\",\"name\":\"determineTargetServerGroup\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":true,\"stageEnd\":true,\"loopStart\":false,\"loopEnd\":false}],\"parentStageId\":\"c3a57f13-c10d-4a90-ad41-1cf58afd42fa\",\"refId\":null,\"requisiteStageRefIds\":null,\"syntheticStageOwner\":\"STAGE_BEFORE\",\"scheduledTime\":0,\"lastModified\":null},{\"id\":\"c3a57f13-c10d-4a90-ad41-1cf58afd42fa\",\"type\":\"destroyServerGroup\",\"name\":\"Cleanup old ASG/build\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"context\":{\"cloudProviderType\":\"aws\",\"cloudProvider\":\"aws\",\"credentials\":\"test\",\"target\":\"oldest_asg_dynamic\",\"cluster\":\"xxx\",\"region\":\"us-east-1\",\"targetLocation\":{\"type\":\"REGION\",\"value\":\"us-east-1\"}},\"immutable\":false,\"initializationStage\":false,\"tasks\":[{\"id\":\"1\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.DisableServerGroupTask\",\"name\":\"disableServerGroup\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":true,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"2\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask\",\"name\":\"monitorServerGroup\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"3\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForAllInstancesNotUpTask\",\"name\":\"waitForNotUpInstances\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"4\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask\",\"name\":\"forceCacheRefresh\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"5\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.DestroyServerGroupTask\",\"name\":\"destroyServerGroup\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"6\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask\",\"name\":\"monitorServerGroup\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"7\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForDestroyedServerGroupTask\",\"name\":\"waitForDestroyedServerGroup\",\"startTime\":null,\"endTime\":null,\"status\":\"NOT_STARTED\",\"stageStart\":false,\"stageEnd\":true,\"loopStart\":false,\"loopEnd\":false}],\"parentStageId\":null,\"refId\":\"8\",\"requisiteStageRefIds\":[\"7\"],\"syntheticStageOwner\":null,\"scheduledTime\":0,\"lastModified\":null}],\"startTime\":1489511659131,\"endTime\":null,\"status\":\"RUNNING\",\"authentication\":{\"user\":\"lgalibert@netflix.com\",\"allowedAccounts\":[\"xxx\"]},\"paused\":null,\"name\":\"Manually deploy all xxx services to prod\",\"pipelineConfigId\":\"c601a550-d711-11e4-bf7d-45f3bbb0924b\",\"trigger\":{\"type\":\"manual\",\"user\":\"lgalibert@netflix.com\",\"notifications\":[]},\"notifications\":[],\"initialConfig\":{}},\"isPipeline\":true,\"parentPipelineStageId\":\"f1c5bd4d-8b14-4cde-a8da-ce00dca149b6\",\"parentPipelineName\":\"Manually deploy all xxx services to prod\",\"parameters\":{}}",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-3-disableCluster.syntheticStageOwner"            : "STAGE_AFTER",
      "keepWaitingPipelines"                                                                       : "false",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44-1-determineTargetServerGroup.context"            : "{\"cluster\":\"xxx\",\"cloudProviderType\":\"aws\",\"regions\":[\"us-east-1\"],\"credentials\":\"test\",\"target\":\"ancestor_asg_dynamic\",\"cloudProvider\":\"aws\"}",
      "paused"                                                                                     : "null",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-1-Deployinuseast1.tasks"                         : "[{\"id\":\"1\",\"implementingClass\":\"com.netflix.spinnaker.orca.kato.pipeline.strategy.DetermineSourceServerGroupTask\",\"name\":\"determineSourceServerGroup\",\"startTime\":1489511660124,\"endTime\":1489511660574,\"status\":\"SUCCEEDED\",\"stageStart\":true,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"2\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask\",\"name\":\"determineHealthProviders\",\"startTime\":1489511660682,\"endTime\":1489511660719,\"status\":\"SUCCEEDED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"3\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.CreateServerGroupTask\",\"name\":\"createServerGroup\",\"startTime\":1489511660774,\"endTime\":1489511661163,\"status\":\"SUCCEEDED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"4\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask\",\"name\":\"monitorDeploy\",\"startTime\":1489511661205,\"endTime\":1489511671241,\"status\":\"CANCELED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"5\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask\",\"name\":\"forceCacheRefresh\",\"status\":\"CANCELED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"6\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupMetadataTagTask\",\"name\":\"tagServerGroup\",\"status\":\"CANCELED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"7\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstancesTask\",\"name\":\"waitForUpInstances\",\"status\":\"CANCELED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"8\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask\",\"name\":\"forceCacheRefresh\",\"status\":\"CANCELED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"9\",\"implementingClass\":\"com.netflix.spinnaker.orca.kato.tasks.JarDiffsTask\",\"name\":\"jarDiffs\",\"status\":\"CANCELED\",\"stageStart\":false,\"stageEnd\":false,\"loopStart\":false,\"loopEnd\":false},{\"id\":\"10\",\"implementingClass\":\"com.netflix.spinnaker.orca.igor.tasks.GetCommitsTask\",\"name\":\"getCommits\",\"status\":\"CANCELED\",\"stageStart\":false,\"stageEnd\":true,\"loopStart\":false,\"loopEnd\":false}]",
      "stage.fc904037-a1ab-412c-bbdf-e8359afd60ea.startTime"                                       : "1489511659415",
      "stageIndex"                                                                                 : "fc904037-a1ab-412c-bbdf-e8359afd60ea,adeba849-eb19-4b25-be36-99458496b37e,adeba849-eb19-4b25-be36-99458496b37e-1-Deployinuseast1,adeba849-eb19-4b25-be36-99458496b37e-3-disableCluster,81aa7749-1756-418f-8bcf-7d26a2d6c13b,8bef96a7-605c-432e-8a30-e6e0697b2f44-1-determineTargetServerGroup,8bef96a7-605c-432e-8a30-e6e0697b2f44",
      "stage.81aa7749-1756-418f-8bcf-7d26a2d6c13b.name"                                            : "Wait for index build and potential rollback",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44.name"                                            : "Cleanup",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44-1-determineTargetServerGroup.syntheticStageOwner": "STAGE_BEFORE",
      "stage.fc904037-a1ab-412c-bbdf-e8359afd60ea.status"                                          : "SUCCEEDED",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-1-Deployinuseast1.name"                          : "Deploy in us-east-1",
      "stage.81aa7749-1756-418f-8bcf-7d26a2d6c13b.scheduledTime"                                   : "0",
      "stage.adeba849-eb19-4b25-be36-99458496b37e.requisiteStageRefIds"                            : "0",
      "stage.81aa7749-1756-418f-8bcf-7d26a2d6c13b.context"                                         : "{\"comments\":\"Wait until the new index is up and built the index and a total of 30 min in case we need to roll back before detroying the old ASG\",\"waitTime\":1800}",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44-1-determineTargetServerGroup.parentStageId"      : "8bef96a7-605c-432e-8a30-e6e0697b2f44",
      "pipelineConfigId"                                                                           : "e6f522d0-ff7c-11e4-a8b3-f5bd0633341b",
      "stage.81aa7749-1756-418f-8bcf-7d26a2d6c13b.initializationStage"                             : "false",
      "stage.81aa7749-1756-418f-8bcf-7d26a2d6c13b.type"                                            : "wait",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44.initializationStage"                             : "false",
      "stage.fc904037-a1ab-412c-bbdf-e8359afd60ea.scheduledTime"                                   : "0",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-3-disableCluster.name"                           : "disableCluster",
      "stage.fc904037-a1ab-412c-bbdf-e8359afd60ea.context"                                         : "{\"cluster\":\"xxx\",\"onlyEnabled\":true,\"regions\":[\"us-east-1\"],\"cloudProviderType\":\"aws\",\"credentials\":\"test\",\"selectionStrategy\":\"NEWEST\",\"cloudProvider\":\"aws\",\"stageDetails\":{\"name\":\"Find AMI\",\"type\":\"findImage\",\"startTime\":1489511659336,\"isSynthetic\":false,\"endTime\":1489511660026},\"amiDetails\":[{\"ami\":\"ami-xxx\",\"imageId\":\"ami-xxx\",\"imageName\":\"xxx\",\"cloudProvider\":\"aws\",\"refId\":\"0\",\"sourceServerGroup\":\"xxx\",\"region\":\"us-east-1\",\"kernelId\":\"aki-xxx\",\"virtualizationType\":\"paravirtual\",\"blockDeviceMappings\":[{\"deviceName\":\"/dev/sda1\",\"ebs\":{\"snapshotId\":\"snap-xxx\",\"volumeSize\":8,\"deleteOnTermination\":true,\"volumeType\":\"standard\",\"encrypted\":false}},{\"virtualName\":\"ephemeral0\",\"deviceName\":\"/dev/sdb\"},{\"virtualName\":\"ephemeral1\",\"deviceName\":\"/dev/sdc\"},{\"virtualName\":\"ephemeral2\",\"deviceName\":\"/dev/sdd\"},{\"virtualName\":\"ephemeral3\",\"deviceName\":\"/dev/sde\"}],\"description\":\"xxx\",\"ownerId\":\"xxx\",\"creationDate\":\"2017-03-14T16:21:07.000Z\",\"imageLocation\":\"xxx/xxx\",\"rootDeviceType\":\"ebs\",\"tags\":[{\"key\":\"creator\",\"value\":\"xxx\"},{\"key\":\"creation_time\",\"value\":\"2017-03-14 16:21:12 UTC\"},{\"key\":\"appversion\",\"value\":\"xxx\"},{\"key\":\"base_ami_version\",\"value\":\"xxx\"},{\"key\":\"build_host\",\"value\":\"xxx\"}],\"public\":false,\"hypervisor\":\"xen\",\"name\":\"xxx\",\"rootDeviceName\":\"/dev/sda1\",\"state\":\"available\",\"productCodes\":[],\"imageType\":\"machine\",\"architecture\":\"x86_64\",\"package_name\":\"xxx\",\"version\":\"1.944\",\"commit\":\"11ddd09\",\"jenkins\":{\"name\":\"xxx\",\"number\":\"1098\",\"host\":\"xxx\"}}],\"batch.task.id.findImage\":206717}",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44-1-determineTargetServerGroup.initializationStage": "false",
      "stage.81aa7749-1756-418f-8bcf-7d26a2d6c13b.status"                                          : "NOT_STARTED",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44.refId"                                           : "3",
      "executingInstance"                                                                          : "i-0a5f4bd4868e3d4b2",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-1-Deployinuseast1.status"                        : "CANCELED",
      "stage.fc904037-a1ab-412c-bbdf-e8359afd60ea.type"                                            : "findImage",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44-1-determineTargetServerGroup.type"               : "determineTargetServerGroup",
      "startTime"                                                                                  : "1489511659315",
      "stage.fc904037-a1ab-412c-bbdf-e8359afd60ea.tasks"                                           : "[{\"id\":\"1\",\"implementingClass\":\"com.netflix.spinnaker.orca.clouddriver.tasks.cluster.FindImageFromClusterTask\",\"name\":\"findImage\",\"startTime\":1489511659417,\"endTime\":1489511660023,\"status\":\"SUCCEEDED\",\"stageStart\":true,\"stageEnd\":true,\"loopStart\":false,\"loopEnd\":false}]",
      "stage.81aa7749-1756-418f-8bcf-7d26a2d6c13b.requisiteStageRefIds"                            : "1",
      "stage.adeba849-eb19-4b25-be36-99458496b37e.initializationStage"                             : "true",
      "stage.fc904037-a1ab-412c-bbdf-e8359afd60ea.endTime"                                         : "1489511660026",
      "stage.adeba849-eb19-4b25-be36-99458496b37e-3-disableCluster.initializationStage"            : "false",
      "stage.8bef96a7-605c-432e-8a30-e6e0697b2f44-1-determineTargetServerGroup.scheduledTime"      : "0",
      "endTime"                                                                                    : "1489511671253"
    ])

    expect:
    repository.retrieve(PIPELINE, "ancient")
  }
}
