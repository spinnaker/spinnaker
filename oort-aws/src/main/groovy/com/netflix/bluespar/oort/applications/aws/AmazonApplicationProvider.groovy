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

package com.netflix.bluespar.oort.applications.aws

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.netflix.bluespar.amazon.security.AmazonClientProvider
import com.netflix.bluespar.amazon.security.AmazonCredentials
import com.netflix.bluespar.oort.applications.Application
import com.netflix.bluespar.oort.applications.ApplicationProvider
import com.netflix.frigga.Names
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.util.StopWatch

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Logger

@Component
class AmazonApplicationProvider implements ApplicationProvider {
  @Override
  List<Application> list() {
    Cacher.get().values() as List
  }

  @Override
  Application get(String name) {
    Cacher.get().get(name)
  }

  @Component
  static class Cacher {
    private static final Logger log = Logger.getLogger(this.class.simpleName)

    private static def firstRun = true
    private static def lock = new ReentrantLock()
    private static def map = new ConcurrentHashMap()
    private static def executorService = Executors.newFixedThreadPool(20)

    @Autowired
    AmazonClientProvider amazonClientProvider

    @Autowired
    AmazonCredentials amazonCredentials

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

      def c = { String region ->
        def client = amazonClientProvider.getAutoScaling(amazonCredentials, region)
        def request = new DescribeAutoScalingGroupsRequest()
        def result = client.describeAutoScalingGroups(request)

        List<AutoScalingGroup> asgs = []
        while (true) {
          asgs.addAll result.autoScalingGroups
          if (result.nextToken) {
            result = client.describeAutoScalingGroups(request.withNextToken(result.nextToken))
          } else {
            break
          }
        }

        for (asg in asgs) {
          def names = Names.parseName(asg.autoScalingGroupName)
          if (!run.containsKey(names.app)) {
            run[names.app] = new Application(name: names.app, type: "Amazon")
          }
        }
      }
      def callables = ["us-east-1", "us-west-1", "us-west-2", "eu-west-1"].collect { c.curry(it) }
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
