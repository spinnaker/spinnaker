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

package com.netflix.spinnaker.gate

import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext
import com.netflix.spinnaker.gate.config.Service
import com.netflix.spinnaker.gate.config.ServiceConfiguration
import com.netflix.spinnaker.gate.services.*
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.OortService
import spock.lang.Specification

import java.util.concurrent.Executors

class ApplicationServiceSpec extends Specification {

  void "should properly aggregated application metadata and application data"() {
    setup:
      HystrixRequestContext.initializeContext()

      def service = new ApplicationService()
      def front50 = Mock(Front50Service)
      def credentialsService = Mock(CredentialsService)
      def oort = Mock(OortService)

      service.front50Service = front50
      service.oortService = oort
      service.credentialsService = credentialsService
      service.executorService = Executors.newFixedThreadPool(1)

    and:
      def testApp = [name: name, attributes: [:], clusters: [prod: [cluster]]]
      def meta = [name: name, email: email, owner: owner]

    when:
      def results = service.all

    then:
      1 * credentialsService.getAccountNames() >> { [account] }
      1 * front50.getAll(account) >> [meta]
      1 == results.size()
      results.first() == [name: name, email: email, owner: owner]

    where:
      name = "foo"
      email = "bar@baz.bz"
      owner = "danw"
      cluster = "cluster1"
      account = "test"
  }

  void "should obey readAccountOverride when building application list retrievers"() {
    setup:
      def credentialsService = Mock(CredentialsService)
      def serviceConfiguration = new ServiceConfiguration(
          services: [new Service(name: "front50", config: [readAccountOverride: "global"])]
      )
      def service = new ApplicationService(
          serviceConfiguration: serviceConfiguration, credentialsService: credentialsService
      )

    when:
      def applicationListRetrievers = service.buildApplicationListRetrievers()

    then:
      0 * credentialsService.getAccountNames()
      applicationListRetrievers*.account == ["global"]
  }

  void "should obey readAccountOverride when building application retrievers"() {
    setup:
      def credentialsService = Mock(CredentialsService)
      def serviceConfiguration = new ServiceConfiguration(services: [
          new Service(name: "front50", config: [readAccountOverride: "global"])
      ])
      def service = new ApplicationService(
          serviceConfiguration: serviceConfiguration, credentialsService: credentialsService
      )

    when:
      def applicationListRetrievers = service.buildApplicationListRetrievers()

    then:
      applicationListRetrievers*.account == ["global"]
  }
}
