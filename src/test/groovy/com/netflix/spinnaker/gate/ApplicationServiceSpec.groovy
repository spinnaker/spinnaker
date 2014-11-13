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
import com.netflix.spinnaker.gate.services.ApplicationService
import com.netflix.spinnaker.gate.services.CredentialsService
import com.netflix.spinnaker.gate.services.Front50Service
import com.netflix.spinnaker.gate.services.OortService
import spock.lang.Specification

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

    and:
      def testApp  = [name: name, attributes: [:], clusters: [prod: [cluster]]]
      def meta = [name: name, email: email, owner: owner]

    when:
      def results = service.all.toBlocking().first()

    then:
      1 * credentialsService.getAccountNames() >> { rx.Observable.from([account]) }
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
}
