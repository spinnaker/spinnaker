package com.netflix.oort.clusters.aws

import com.netflix.frigga.Names
import com.netflix.frigga.ami.AppVersion
import com.netflix.oort.clusters.Cluster
import com.netflix.oort.clusters.ClusterProvider
import com.netflix.oort.remoting.AggregateRemoteResource
import com.netflix.oort.remoting.RemoteResource
import com.netflix.oort.spring.ApplicationContextHolder
import groovy.util.logging.Log4j
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import org.apache.commons.codec.binary.Base64
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.util.StopWatch

@Component
class AmazonClusterProvider implements ClusterProvider {
  @Autowired
  AggregateRemoteResource edda

  @Override
  Map<String, List<Cluster>> get(String deployable) {
    Cacher.get().get(deployable)
  }

  @Override
  List<Cluster> getByName(String deployable, String clusterName) {
    Cacher.get().get(deployable)?.get(clusterName)
  }

  @Override
  Cluster getByNameAndZone(String deployable, String clusterName, String zone) {
    Cacher.get().get(deployable)?.get(clusterName)?.find { it.zone == zone }
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

    @Autowired
    RemoteResource bakery

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
      log.info "Beginning caching amazon clusters."
      def asgCallable = { String region, RemoteResource remoteResource ->
        [(region): remoteResource.query("/REST/v2/aws/autoScalingGroups;_expand").collectEntries {
          [(it.autoScalingGroupName): it]
        }]
      }
      def launchConfigCallable = { String region, RemoteResource remoteResource ->
        [(region): remoteResource.query("/REST/v2/aws/launchConfigurations;_expand").collectEntries {
          [(it.launchConfigurationName): it]
        }]
      }
      def imageCallable = { String region, RemoteResource remoteResource ->
        [(region): remoteResource.query("/REST/v2/aws/images;_expand").collectEntries {
          [(it.imageId): it]
        }]
      }
      def callables = ["us-east-1", "us-west-1", "us-west-2", "eu-west-1"].collect { region ->
        [asgCallable, launchConfigCallable, imageCallable].collect {
          it.curry(region, edda.getRemoteResource(region))
        }
      }?.flatten()

      def results = executorService.invokeAll(callables)*.get()
      def asgCache = (Map<String, List<Map>>)results.getAt(0) + results.getAt(3) + results.getAt(6) + results.getAt(9)
      def launchConfigCache = results.getAt(1) + results.getAt(4) + results.getAt(7) + results.getAt(10)
      def imageCache = results.getAt(2) + results.getAt(5) + results.getAt(8) + results.getAt(11)

      for (Map.Entry entry : asgCache) {
        def region = entry.key
        def asgs = entry.value?.values() as List

        for (Map asg in asgs) {
          try {
            def names = Names.parseName(asg.autoScalingGroupName)
            if (!run.containsKey(names.app)) {
              run[names.app] = [:]
            }
            if (!run[names.app].containsKey(names.cluster)) {
              run[names.app][names.cluster] = []
            }
            def cluster = new AmazonCluster(name: names.cluster, zone: region, serverGroups: [])
            ApplicationContextHolder.applicationContext.autowireCapableBeanFactory.autowireBean(cluster)

            Map launchConfig = launchConfigCache[region][asg.launchConfigurationName] as Map
            String userData = new String(Base64.decodeBase64(launchConfig.userData as String))

            def resp = [name: names.group, region: region, cluster: names.cluster, instances: asg.instances,
                        created: asg.createdTime, launchConfig: launchConfig, userData: userData, capacity: [min: asg.minSize, max: asg.maxSize, desired: asg.desiredCapacity]]

            Map image = imageCache[region][launchConfig.imageId] as Map
            def buildVersion = image?.tags?.find { it.key == "appversion" }?.value
            if (buildVersion) {
              def props = AppVersion.parseName(buildVersion)?.properties
              if (props) {
                resp.buildInfo = ["buildNumber", "commit", "packageName", "buildJobName"].collectEntries {
                  [(it): props[it]]
                }
              }
            }

            def serverGroup = new AmazonServerGroup(names.group, resp)
            cluster.serverGroups << serverGroup
            run[names.app][names.cluster] << cluster
          } catch (IGNORE) {}
        }
      }
      if (!lock.isLocked()) {
        lock.lock()
      }
      map = run.sort { a, b -> a.key.toLowerCase() <=> b.key.toLowerCase() }
      lock.unlock()
      if (firstRun) {
        firstRun = false
      }
      stopwatch.stop()
      log.info "Done caching amazon clusters in ${stopwatch.shortSummary()}"
    }
  }

}
