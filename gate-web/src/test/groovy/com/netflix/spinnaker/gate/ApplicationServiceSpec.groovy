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
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.OortService
import spock.lang.Specification

import java.util.concurrent.Executors

class ApplicationServiceSpec extends Specification {

  void "should properly aggregate application data from Front50 and Oort"() {
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
      def oortApp = [name: name, attributes: [oortName: name, name: "bad"], clusters: [prod: [cluster]]]
      def front50App = [name: name, email: email, owner: owner]

    when:
      def app = service.get(name)

    then:
      1 * front50.credentials >> { [] }
      1 * credentialsService.getAccounts() >> [[name: account, type: providerType]]
      1 * oort.getApplication(name) >> oortApp
      1 * front50.getMetaData(account, name) >> front50App

      app == [name: name, attributes: (oortApp.attributes + front50App), clusters: oortApp.clusters]

    where:
      name = "foo"
      email = "bar@baz.bz"
      owner = "danw"
      cluster = "cluster1"
      account = "test"
      providerType = "aws"
  }

  void "should return null when no application attributes are available"() {
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

    when:
    def app = service.get(name)

    then:
    1 * front50.credentials >> { [] }
    1 * credentialsService.getAccounts() >> [[name: account, type: providerType]]
    1 * oort.getApplication(name) >> null
    1 * front50.getMetaData(account, name) >> null

    app == null

    where:
    name = "foo"
    account = "test"
    providerType = "google"
  }

  void "should favor available global registries when building application list retrievers"() {
    setup:
      def credentialsService = Mock(CredentialsService)
      def front50Service = Mock(Front50Service)
      def service = new ApplicationService(credentialsService: credentialsService, front50Service: front50Service)

    when:
      def applicationListRetrievers = service.buildApplicationListRetrievers()

    then:
      1 * front50Service.credentials >> [[name: account, global: true]]
      0 * credentialsService.getAccounts()
      applicationListRetrievers.findAll { it.getMetaClass().getMetaProperty("account") != null }
          .collect { it.@account }.unique() == [account]

    where:
      account = "global"
  }

  void "should favor available global registries when building application retrievers"() {
    setup:
      def credentialsService = Mock(CredentialsService)
      def front50Service = Mock(Front50Service)
      def service = new ApplicationService(credentialsService: credentialsService, front50Service: front50Service)

    when:
      def applicationListRetrievers = service.buildApplicationListRetrievers()

    then:
      1 * front50Service.credentials >> [[name: account, global: true]]
      applicationListRetrievers.findAll { it.getMetaClass().getMetaProperty("account") != null }
          .collect { it.@account }.unique() == [account]


    where:
      account = "global"
  }

  void "should retrieve from both oort and front50 when global registry exists"() {
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
      def oortApp = [name: oortName]
      def front50App = [name: name]

    when:
      def apps = service.getAll()

    then:
      1 * oort.getApplications() >> [oortApp]
      1 * front50.getAll(account) >> [front50App]
      1 * front50.credentials >> [globalAccount]

      2 == apps.size()
      apps*.name.containsAll(oortName, name)
      0 * credentialsService.getAccounts()

    where:
      oortName = "barapp"
      name = "foo"
      account = "global"
      globalAccount = [name: account, global: true]
  }

  void "should properly merge retrieved apps from oort and front50"() {
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
      def oortApp = [name: name.toUpperCase(), attributes: [name: name], clusters: [prod: [[name: "cluster-name"]]]]
      def front50App = [name: name.toLowerCase(), email: email]

    when:
      def apps = service.getAll()

    then:
      1 * oort.getApplications() >> [oortApp]
      1 * front50.getAll(account) >> [front50App]
      1 * front50.credentials >> [globalAccount]

      1 == apps.size()
      apps[0].email == email
      apps[0].name == name
      apps[0].clusters == null

    where:
      name = "foo"
      email = "foo@bar.bz"
      account = "global"
      globalAccount = [name: account, global: true]
  }
}
