package com.netflix.oort.deployables

import com.netflix.frigga.Names
import com.netflix.oort.remoting.AggregateRemoteResource
import com.netflix.oort.remoting.RemoteResource
import groovy.util.logging.Log4j
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.util.StopWatch

@Component
class EddaDeployableProvider implements DeployableProvider {
  @Override
  List<Deployable> list() {
    Cacher.get().values() as List
  }

  @Override
  Deployable get(String name) {
    Cacher.get().get(name)
  }

  @Component
  @Log4j
  static class Cacher {
    private static def firstRun = true
    private static def lock = new ReentrantLock()
    private static def map = new ConcurrentHashMap()
    private static def executorService = Executors.newFixedThreadPool(20)

    @Autowired
    AggregateRemoteResource edda

    static Map get() {
      lock.lock()
      def m = new HashMap(map)
      lock.unlock()
      m
    }

    @Scheduled(fixedRate = 30000l)
    void cacheClusters() {
      if (firstRun) {
        lock.lock()
      }

      def run = new ConcurrentHashMap()
      def stopwatch = new StopWatch()
      stopwatch.start()
      log.info "Beginning caching."
      def c = { String region, RemoteResource remoteResource ->
        List<String> asgs = remoteResource.query("/REST/v2/aws/autoScalingGroups")

        for (asg in asgs) {
          def names = Names.parseName(asg)
          if (!run.containsKey(names.app)) {
            run[names.app] = new Deployable(name: names.app, type: "Amazon")
          }
        }
      }
      def callables = ["us-east-1", "us-west-1", "us-west-2", "eu-west-1"].collect { c.curry(it, edda.getRemoteResource(it)) }
      executorService.invokeAll(callables)*.get()
      if (!lock.isLocked()) {
        lock.lock()
      }
      map = run.sort { a, b -> a.key.toLowerCase() <=> b.key.toLowerCase() }
      lock.unlock()
      if (firstRun) {
        firstRun = false
      }
      stopwatch.stop()
      log.info "Done caching in ${stopwatch.shortSummary()}"
    }
  }
}
