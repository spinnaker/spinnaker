package com.netflix.spinnaker.orca.pipeline.persistence.jedis

import java.util.function.Function
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.*
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCommands
import redis.clients.util.Pool
import rx.Observable
import rx.Scheduler
import rx.schedulers.Schedulers
import static java.util.concurrent.Executors.newFixedThreadPool

@Component
@Slf4j
@CompileStatic
class JedisExecutionRepository implements ExecutionRepository {

  private final Pool<Jedis> jedisPool
  private final ObjectMapper mapper = new OrcaObjectMapper()
  private final int chunkSize
  private final Scheduler queryAllScheduler = Schedulers.from(newFixedThreadPool(10))
  private final Scheduler queryByAppScheduler

  @Autowired
  JedisExecutionRepository(
    Pool<Jedis> jedisPool,
    @Value('${threadPool.executionRepository:150}') int threadPoolSize,
    @Value('${chunkSize.executionRepository:75}') int threadPoolChunkSize
  ) {
    this.jedisPool = jedisPool
    this.chunkSize = threadPoolChunkSize
    this.queryByAppScheduler = Schedulers.from(newFixedThreadPool(threadPoolSize))
  }

  @Override
  void store(Orchestration orchestration) {
    withJedis { Jedis jedis ->
      storeExecutionInternal(jedis, orchestration)
    }
  }

  @Override
  void store(Pipeline pipeline) {
    withJedis { Jedis jedis ->
      storeExecutionInternal(jedis, pipeline)
    }
  }

  @Override
  void storeStage(PipelineStage stage) {
    withJedis { Jedis jedis ->
      storeStageInternal(jedis, Pipeline, stage)
    }
  }

  @Override
  void storeStage(OrchestrationStage stage) {
    withJedis { Jedis jedis ->
      storeStageInternal(jedis, Orchestration, stage)
    }
  }

  @Override
  Pipeline retrievePipeline(String id) {
    withJedis { Jedis jedis ->
      retrieveInternal(jedis, Pipeline, id)
    }
  }

  @Override
  void deletePipeline(String id) {
    withJedis { Jedis jedis ->
      deleteInternal(jedis, Pipeline, id)
    }
  }

  @Override
  Observable<Pipeline> retrievePipelines() {
    all(Pipeline)
  }

  @Override
  Observable<Pipeline> retrievePipelinesForApplication(String application) {
    allForApplication(Pipeline, application)
  }

  @Override
  Orchestration retrieveOrchestration(String id) {
    withJedis { Jedis jedis ->
      retrieveInternal(jedis, Orchestration, id)
    }
  }

  @Override
  void deleteOrchestration(String id) {
    withJedis { Jedis jedis ->
      deleteInternal(jedis, Orchestration, id)
    }
  }

  @Override
  Observable<Orchestration> retrieveOrchestrations() {
    all(Orchestration)
  }

  @Override
  Observable<Orchestration> retrieveOrchestrationsForApplication(String application) {
    allForApplication(Orchestration, application)
  }

  private void storeExecutionInternal(JedisCommands jedis, Execution execution) {
    def prefix = execution.getClass().simpleName.toLowerCase()

    if (!execution.id) {
      execution.id = UUID.randomUUID().toString()
      jedis.sadd(alljobsKey(execution.getClass()), execution.id)
      def appKey = appKey(execution.getClass(), execution.application)
      jedis.sadd(appKey, execution.id)
    }
    def json = mapper.writeValueAsString(execution)

    def key = "${prefix}:$execution.id"
    jedis.hset(key, "config", json)
  }

  private <T extends Execution> void storeStageInternal(Jedis jedis, Class<T> type, Stage<T> stage) {
    def json = mapper.writeValueAsString(stage)
    def key = "${type.simpleName.toLowerCase()}:stage:${stage.id}"
    jedis.hset(key, "config", json)
  }

  @CompileDynamic
  private <T extends Execution> T retrieveInternal(Jedis jedis, Class<T> type, String id) throws ExecutionNotFoundException {
    def key = "${type.simpleName.toLowerCase()}:$id"
    if (jedis.exists(key)) {
      def json = jedis.hget(key, "config")
      def execution = mapper.readValue(json, type)
      return sortStages(jedis, execution, type)
    } else {
      throw new ExecutionNotFoundException("No ${type.simpleName} found for $id")
    }
  }

  @CompileDynamic
  private <T extends Execution> T sortStages(Jedis jedis, T execution, Class<T> type) {
    List<Stage<T>> reorderedStages = []
    execution.stages.findAll { it.parentStageId == null }.each { Stage<T> parentStage ->
      reorderedStages << parentStage

      def children = new LinkedList<Stage<T>>(execution.stages.findAll { it.parentStageId == parentStage.id })
      while (!children.isEmpty()) {
        def child = children.remove(0)
        children.addAll(0, execution.stages.findAll { it.parentStageId == child.id })
        reorderedStages << child
      }
    }
    List<Stage<T>> retrievedStages = retrieveStages(jedis, type, reorderedStages.collect { it.id })
    execution.stages = reorderedStages.collect {
      def explicitStage = retrievedStages.find { stage -> stage?.id == it.id } ?: it
      explicitStage.execution = execution
      return explicitStage
    }
    return execution
  }

  private <T extends Execution> List<Stage<T>> retrieveStages(Jedis jedis, Class<T> type, List<String> ids) {
    def pipeline = jedis.pipelined()
    ids.each { id ->
      pipeline.hget("${type.simpleName.toLowerCase()}:stage:${id}", "config")
    }
    def results = pipeline.syncAndReturnAll()
    return results.collect { it ? mapper.readValue(it as String, Stage) : null }
  }

  private <T extends Execution> void deleteInternal(Jedis jedis, Class<T> type, String id) {
    def key = "${type.simpleName.toLowerCase()}:$id"
    try {
      T item = retrieveInternal(jedis, type, id)
      def appKey = appKey(type, item.application)
      jedis.srem(appKey, id)

      item.stages.each { Stage stage ->
        def stageKey = "${type.simpleName.toLowerCase()}:stage:${stage.id}"
        jedis.hdel(stageKey, "config")
      }
    } catch (ExecutionNotFoundException ignored) {
      // do nothing
    } finally {
      jedis.hdel(key, "config")
      jedis.srem(alljobsKey(type), id)
    }
  }

  private <T extends Execution> Observable<T> all(Class<T> type) {
    retrieveObservable(type, alljobsKey(type), queryAllScheduler)
  }

  private <T extends Execution> Observable<T> allForApplication(Class<T> type, String application) {
    retrieveObservable(type, appKey(type, application), queryByAppScheduler)
  }

  @CompileDynamic
  private <T extends Execution> Observable<T> retrieveObservable(Class<T> type, String lookupKey, Scheduler scheduler) {
    Observable
      .just(lookupKey)
      .flatMapIterable { String key -> withJedis { Jedis jedis -> jedis.smembers(lookupKey) } }
      .buffer(chunkSize)
      .flatMap { Collection<String> ids ->
      Observable
        .from(ids)
        .flatMap { String executionId ->
        withJedis { Jedis jedis ->
          try {
            return Observable.just(retrieveInternal(jedis, type, executionId))
          } catch (ExecutionNotFoundException ignored) {
            log.info("Execution (${executionId}) does not exist")
            delete(executionId)
            jedisCommands.srem(lookupKey, executionId)
          } catch (Exception e) {
            log.error("Failed to retrieve execution '${executionId}', message: ${e.message}")
          }
          return Observable.empty()
        }
      }
        .subscribeOn(scheduler)
    }
  }

  private String alljobsKey(Class type) {
    "allJobs:${type.simpleName.toLowerCase()}"
  }

  private String appKey(Class type, String app) {
    "${type.simpleName.toLowerCase()}:app:${app}"
  }

  private <T> T withJedis(Function<Jedis, T> action) {
    jedisPool.resource.withCloseable(action.&apply)
  }
}
