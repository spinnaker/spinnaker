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
import com.netflix.spinnaker.gate.services.ApplicationService
import com.netflix.spinnaker.gate.services.CredentialsService
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.KatoService
import com.netflix.spinnaker.gate.services.internal.OortService
import com.netflix.spinnaker.gate.services.internal.OrcaService
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.Executors

class ApplicationServiceSpec extends Specification {

  void "should properly aggregate application data from Front50 and Oort"() {
    setup:
    HystrixRequestContext.initializeContext()

    def service = new ApplicationService()
    def front50 = Mock(Front50Service)
    def oort = Mock(OortService)
    def config = new ServiceConfiguration(services: [front50: new Service()])

    service.serviceConfiguration = config
    service.front50Service = front50
    service.oortService = oort
    service.executorService = Executors.newFixedThreadPool(1)

    and:
    def oortApp = [name: name, attributes: [oortName: name, name: "bad"], clusters: [(account): [cluster]]]
    def front50App = [name: name, email: email, owner: owner]

    when:
    def app = service.get(name)

    then:
    1 * front50.credentials >> [[name: account, global: true]]
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

  void "should include accounts from front50 and from oort clusters"() {
    setup:
    HystrixRequestContext.initializeContext()

    def service = new ApplicationService()
    def front50 = Mock(Front50Service)
    def oort = Mock(OortService)
    def config = new ServiceConfiguration(services: [front50: new Service()])

    service.serviceConfiguration = config
    service.front50Service = front50
    service.oortService = oort
    service.executorService = Executors.newFixedThreadPool(1)

    and:
    def oortApp = [name: name, attributes: [oortName: name, name: "bad"], clusters: [(oortAccount): [cluster]]]
    def front50App = [name: name, email: email, owner: owner]

    when:
    def app = service.get(name)

    then:
    1 * front50.credentials >> [[name: front50Account, global: true]]
    1 * oort.getApplication(name) >> oortApp
    1 * front50.getMetaData(front50Account, name) >> front50App

    app == [name: name, attributes: (oortApp.attributes + front50App + [accounts: [oortAccount, front50Account].toSet().sort().join(',')]), clusters: oortApp.clusters]

    where:
    name = "foo"
    email = "bar@baz.bz"
    owner = "danw"
    cluster = "cluster1"
    oortAccount = "test"
    front50Account = "prod"
    providerType = "aws"

  }

  void "should return null when application account does not match includedAccounts"() {
    setup:
    HystrixRequestContext.initializeContext()

    def service = new ApplicationService()
    def front50 = Mock(Front50Service)
    def oort = Mock(OortService)
    def config = new ServiceConfiguration(services: [front50: new Service(config: [includedAccounts: includedAccount])])

    service.serviceConfiguration = config
    service.front50Service = front50
    service.oortService = oort
    service.executorService = Executors.newFixedThreadPool(1)

    when:
    def app = service.get(name)

    then:
    1 * front50.credentials >> [[name: account, global: true]]
    1 * oort.getApplication(name) >> null
    1 * front50.getMetaData(account, name) >> [name: name, foo: 'bar']

    (app == null) == expectedNull

    where:
    account | includedAccount | expectedNull
    "test"  | "test"          | false
    "prod"  | "test"          | true
    "prod"  | "prod,test"     | false
    "prod"  | "test,dev"      | true
    "test"  | null            | false
    "test"  | ""              | false

    name = "foo"
    providerType = "aws"
  }


  void "should return null when no application attributes are available"() {
    setup:
    HystrixRequestContext.initializeContext()

    def service = new ApplicationService()
    def front50 = Mock(Front50Service)
    def oort = Mock(OortService)
    def config = new ServiceConfiguration(services: [front50: new Service()])

    service.serviceConfiguration = config
    service.front50Service = front50
    service.oortService = oort
    service.executorService = Executors.newFixedThreadPool(1)

    when:
    def app = service.get(name)

    then:
    1 * front50.credentials >> [[name: account, global: true]]
    1 * oort.getApplication(name) >> null
    1 * front50.getMetaData(account, name) >> null

    app == null

    where:
    name = "foo"
    account = "test"
  }

  void "should build application list retrievers for global application registries"() {
    setup:
    def front50Service = Mock(Front50Service)
    def config = new ServiceConfiguration(services: [front50: new Service()])
    def service = new ApplicationService(front50Service: front50Service, serviceConfiguration: config)

    when:
    def applicationListRetrievers = service.buildApplicationListRetrievers()

    then:
    1 * front50Service.credentials >> [[name: account, global: true]]
    applicationListRetrievers.findAll { it.getMetaClass().getMetaProperty("account") != null }
      .collect { it.@account }.unique() == [account]

    where:
    account = "global"
  }

  void "should build application retrievers for global application registries"() {
    setup:
    def front50Service = Mock(Front50Service)
    def config = new ServiceConfiguration(services: [front50: new Service()])
    def service = new ApplicationService(front50Service: front50Service, serviceConfiguration: config)

    when:
    def applicationListRetrievers = service.buildApplicationListRetrievers()

    then:
    1 * front50Service.credentials >> [[name: account, global: true]]
    applicationListRetrievers.findAll { it.getMetaClass().getMetaProperty("account") != null }
      .collect { it.@account }.unique() == [account]

    where:
    account = "global"
  }

  void "should properly merge retrieved apps from oort and front50"() {
    setup:
    HystrixRequestContext.initializeContext()

    def service = new ApplicationService()
    def front50 = Mock(Front50Service)
    def oort = Mock(OortService)
    def config = new ServiceConfiguration(services: [front50: new Service()])

    service.serviceConfiguration = config
    service.front50Service = front50
    service.oortService = oort
    service.executorService = Executors.newFixedThreadPool(1)

    and:
    def oortApp = [name: name.toUpperCase(), attributes: [name: name], clusters: [prod: [[name: "cluster-name"]]]]
    def front50App = [name: name.toLowerCase(), email: email]

    when:
    service.tick()
    def apps = service.getAll()

    then:
    1 * oort.getApplications() >> [oortApp]
    1 * front50.getAll(account) >> [front50App] >> { throw new SocketTimeoutException() }
    1 * front50.credentials >> [globalAccount]

    1 == apps.size()
    apps[0].email == email
    apps[0].name == name
    apps[0].clusters == null

    when: "should return last known good values if an exception is thrown"
    service.tick()
    def allApps = service.getAll()
    def singleApp = service.get(name)

    then:
    1 * front50.getMetaData(account, name) >> { throw new SocketTimeoutException() }
    1 * front50.getAll(account) >> { throw new SocketTimeoutException() }
    2 * front50.credentials >> [globalAccount]

    1 == allApps.size()
    singleApp.name == allApps[0].name

    where:
    name = "foo"
    email = "foo@bar.bz"
    account = "global"
    globalAccount = [name: account, global: true]
  }
}
