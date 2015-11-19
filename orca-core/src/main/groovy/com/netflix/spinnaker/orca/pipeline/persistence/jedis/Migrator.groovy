package com.netflix.spinnaker.orca.pipeline.persistence.jedis

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import rx.Observable

@Component
@CompileStatic
class Migrator implements ApplicationListener<ContextRefreshedEvent> {

  private final Pool<Jedis> jedisPool
  private final ExecutionRepository executionRepository

  @Autowired
  Migrator(ExecutionRepository executionRepository, Pool<Jedis> jedisPool) {
    this.executionRepository = executionRepository
    this.jedisPool = jedisPool
  }

  @CompileDynamic
  @Override
  void onApplicationEvent(ContextRefreshedEvent event) {
    pipelines()
      .mergeWith(orchestrations())
    .doOnCompleted({ println "DONE"})
      .subscribe({ Execution execution ->
      if (execution.version == 2 && execution.stages.any { it.status == ExecutionStatus.SUSPENDED}) {
        println "DELETING $execution.id"
        executionRepository."delete${execution.getClass().simpleName}"(execution.id)
      }
    })
  }

  public Observable<? extends Execution> orchestrations() {
    executionRepository.retrieveOrchestrations()
  }

  public Observable<? extends Execution> pipelines() {
    executionRepository.retrievePipelines()
  }
}
