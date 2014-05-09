/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.asgard.oort.deployables.aws

import com.netflix.asgard.oort.deployables.Deployable
import com.netflix.asgard.oort.deployables.DeployableProvider
import com.netflix.asgard.oort.remoting.AggregateRemoteResource
import com.netflix.asgard.oort.remoting.RemoteResource
import groovy.util.logging.Log4j

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.util.StopWatch

@Component
class Front50DeployableProvider implements DeployableProvider {
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

    @Autowired
    RemoteResource front50

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

      def run
      def stopwatch = new StopWatch()
      log.info "Begin First50 Deployable caching..."
      stopwatch.start()
      List<Map> apps = (List<Map>) front50.query("")
      run = apps.collectEntries { Map obj ->
        [(obj.name?.toLowerCase()): new Deployable(name: obj.name?.toLowerCase(), type: "Amazon",
            attributes: (Map<String, String>)obj.collectEntries { k, v -> [(k): v]})]
      }
      if (!lock.isLocked()) {
        lock.lock()
      }
      map = run
      lock.unlock()
      log.info "Done caching Front50 apps in ${stopwatch.shortSummary()}"
    }
  }
}
