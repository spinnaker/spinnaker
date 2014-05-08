package com.netflix.oort.controllers

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
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestTemplate
import rx.schedulers.Schedulers

@RestController
@RequestMapping("/deployables")
class DeployableController {

  @Autowired
  RemoteResource bakery

  @RequestMapping(method = RequestMethod.GET)
  def list() {
    def cache = Cacher.get()
    cache.inject(new ConcurrentHashMap<>()) { Map map, String deployable, Map v ->
      if (!map.containsKey(deployable)) {
        map[deployable] = [instanceCount:0, asgCount:0, attributes: v.attributes]
      }
      v.clusters.each { String clusterName, Map clusterRegions ->
        clusterRegions.each { String regionName, List<Map> asg ->
          map[deployable].asgCount++
          map[deployable].instanceCount += asg.instances.size()
        }
      }
      map
    }
  }

  @RequestMapping(value = "/{name}/images")
  def getImages(@PathVariable("name") String name) {
    def rt = new RestTemplate()

    rx.Observable.from(["us-east-1", "us-west-1", "us-west-2", "eu-west-1"]).flatMap {
      rx.Observable.from(it).observeOn(Schedulers.io()).map { String region ->
        def list = bakery.query("/api/v1/${region}/bake/;package=${name};store_type=ebs;region=${region};vm_type=pv;base_os=ubuntu;base_label=release")
        list.findAll { it.ami } collect {
          def version = it.ami_name?.split('-')?.getAt(1..2)?.join('.')
          [name: it.ami, region: region, version: version]
        }
      }
    }.reduce([], { objs, obj ->
      if (obj) {
        objs << obj
      }
      objs
    }).toBlockingObservable().first()?.flatten()
  }

  @Component
  @Log4j
  static class Cacher {
    private static def firstRun = true
    private static def lock = new ReentrantLock()
    private static def map = new ConcurrentHashMap()
    private static def executorService = Executors.newFixedThreadPool(20)

    @Autowired
    RemoteResource front50

    @Autowired
    AggregateRemoteResource edda

    static Map get() {
      lock.lock()
      def m = (Map) map.clone()
      lock.unlock()
      m
    }

    @Scheduled(fixedRate = 30000l)
    void cacheClusters() {
      if (firstRun) {
        lock.lock()
      }

      List<Map> apps = (List<Map>) front50.query("")
      def simpleDb = apps.collectEntries { Map obj ->
        def m = [:]
        obj.each { k, v -> m[k] = v }
        [(obj.name?.toLowerCase()): obj.collectEntries { k, v -> [(k): v]}]
      }

      def run = new ConcurrentHashMap()
      def stopwatch = new StopWatch()
      stopwatch.start()
      log.info "Beginning caching."
      def c = { String region, RemoteResource remoteResource ->
        List<Map> asgs = remoteResource.query("/REST/v2/aws/autoScalingGroups;_expand")

        for (asg in asgs) {
          def names = Names.parseName(asg.autoScalingGroupName)
          if (!run.containsKey(names.app)) {
            run[names.app] = new ConcurrentHashMap()
          }
          if (!run[names.app].containsKey("attributes") && simpleDb.containsKey(names.app.toLowerCase())) {
            run[names.app]["attributes"] = simpleDb[names.app.toLowerCase()]
          }
          if (!run[names.app].containsKey("clusters")) {
            run[names.app]["clusters"] = new ConcurrentHashMap()
          }
          if (!run[names.app]["clusters"].containsKey(names.cluster)) {
            run[names.app]["clusters"][names.cluster] = new ConcurrentHashMap()
          }
          if (!run[names.app]["clusters"][names.cluster].containsKey(region)) {
            run[names.app]["clusters"][names.cluster][region] = []
          }
          run[names.app]["clusters"][names.cluster][region] << asg
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
