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
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.Executors

class ApplicationServiceSpec extends Specification {

  void "should properly aggregate application data from Front50 and Clouddriver"() {
    setup:
    HystrixRequestContext.initializeContext()

    def service = new ApplicationService()
    def front50 = Mock(Front50Service)
    def clouddriver = Mock(ClouddriverService)
    def config = new ServiceConfiguration(services: [front50: new Service()])

    service.serviceConfiguration = config
    service.front50Service = front50
    service.clouddriverService = clouddriver
    service.executorService = Executors.newFixedThreadPool(1)

    and:
    def clouddriverApp = [name: name, attributes: [clouddriverName: name, name: "bad"], clusters: [(account): [cluster]]]
    def front50App = [name: name, email: email, owner: owner]

    when:
    def app = service.getApplication(name)

    then:
    1 * front50.credentials >> [[name: account, global: true]]
    1 * clouddriver.getApplication(name) >> clouddriverApp
    1 * front50.getApplication(account, name) >> front50App

    app == [name: name, attributes: (clouddriverApp.attributes + front50App), clusters: clouddriverApp.clusters]

    where:
    name = "foo"
    email = "bar@baz.bz"
    owner = "danw"
    cluster = "cluster1"
    account = "test"
    providerType = "aws"
  }

  void "should include accounts from front50 and from clouddriver clusters"() {
    setup:
    HystrixRequestContext.initializeContext()

    def service = new ApplicationService()
    def front50 = Mock(Front50Service)
    def clouddriver = Mock(ClouddriverService)
    def config = new ServiceConfiguration(services: [front50: new Service()])

    service.serviceConfiguration = config
    service.front50Service = front50
    service.clouddriverService = clouddriver
    service.executorService = Executors.newFixedThreadPool(1)

    and:
    def clouddriverApp = [name: name, attributes: [clouddriverName: name, name: "bad"], clusters: [(clouddriverAccount): [cluster]]]
    def front50App = [name: name, email: email, owner: owner]

    when:
    def app = service.getApplication(name)

    then:
    1 * front50.credentials >> [[name: front50Account, global: true]]
    1 * clouddriver.getApplication(name) >> clouddriverApp
    1 * front50.getApplication(front50Account, name) >> front50App

    app == [name: name, attributes: (clouddriverApp.attributes + front50App + [accounts: [clouddriverAccount, front50Account].toSet().sort().join(',')]), clusters: clouddriverApp.clusters]

    where:
    name = "foo"
    email = "bar@baz.bz"
    owner = "danw"
    cluster = "cluster1"
    clouddriverAccount = "test"
    front50Account = "prod"
    providerType = "aws"

  }

  void "should return null when application account does not match includedAccounts"() {
    setup:
    HystrixRequestContext.initializeContext()

    def service = new ApplicationService()
    def front50 = Mock(Front50Service)
    def clouddriver = Mock(ClouddriverService)
    def config = new ServiceConfiguration(services: [front50: new Service(config: [includedAccounts: includedAccount])])

    service.serviceConfiguration = config
    service.front50Service = front50
    service.clouddriverService = clouddriver
    service.executorService = Executors.newFixedThreadPool(1)

    when:
    def app = service.getApplication(name)

    then:
    1 * front50.credentials >> [[name: account, global: true]]
    1 * clouddriver.getApplication(name) >> null
    1 * front50.getApplication(account, name) >> [name: name, foo: 'bar']

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
    def clouddriver = Mock(ClouddriverService)
    def config = new ServiceConfiguration(services: [front50: new Service()])

    service.serviceConfiguration = config
    service.front50Service = front50
    service.clouddriverService = clouddriver
    service.executorService = Executors.newFixedThreadPool(1)

    when:
    def app = service.getApplication(name)

    then:
    1 * front50.credentials >> [[name: account, global: true]]
    1 * clouddriver.getApplication(name) >> null
    1 * front50.getApplication(account, name) >> null

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
    def applicationListRetrievers = service.buildApplicationListRetrievers(false)

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
    def applicationListRetrievers = service.buildApplicationListRetrievers(false)

    then:
    1 * front50Service.credentials >> [[name: account, global: true]]
    applicationListRetrievers.findAll { it.getMetaClass().getMetaProperty("account") != null }
      .collect { it.@account }.unique() == [account]

    where:
    account = "global"
  }

  void "should properly merge retrieved apps from clouddriver and front50"() {
    setup:
    HystrixRequestContext.initializeContext()

    def service = new ApplicationService()
    def front50 = Mock(Front50Service)
    def clouddriver = Mock(ClouddriverService)
    def config = new ServiceConfiguration(services: [front50: new Service()])

    service.serviceConfiguration = config
    service.front50Service = front50
    service.clouddriverService = clouddriver
    service.executorService = Executors.newFixedThreadPool(1)

    and:
    def clouddriverApp = [name: name.toUpperCase(), attributes: [name: name], clusters: [prod: [[name: "cluster-name"]]]]
    def front50App = [name: name.toLowerCase(), email: email]

    when:
    def apps = service.getAllApplications()

    then:
    1 * clouddriver.getApplications(false) >> [clouddriverApp]
    1 * front50.getAllApplications(account) >> [front50App] >> { throw new SocketTimeoutException() }
    1 * front50.credentials >> [globalAccount]

    1 == apps.size()
    service.allApplicationsCache.set(apps)
    apps[0].email == email
    apps[0].name == name
    apps[0].clusters == null

    when: "should return last known good values if an exception is thrown"
    def allApps = service.getAllApplications()
    def singleApp = service.getApplication(name)

    then:
    1 * front50.getApplication(account, name) >> { throw new SocketTimeoutException() }
    1 * front50.getAllApplications(account) >> { throw new SocketTimeoutException() }
    2 * front50.credentials >> [globalAccount]

    1 == allApps.size()
    singleApp.name == allApps[0].name

    where:
    name = "foo"
    email = "foo@bar.bz"
    account = "global"
    globalAccount = [name: account, global: true]
  }

  @Unroll
  void "should merge accounts"() {
    expect:
    ApplicationService.mergeAccounts(accounts1, accounts2) == mergedAccounts

    where:
    accounts1   | accounts2     || mergedAccounts
    "prod,test" | "secret,test" || "prod,secret,test"
    "prod,test" | null          || "prod,test"
    null        | "prod,test"   || "prod,test"
    "prod"      | "test"        || "prod,test"
    null        | null          || ""
  }
}
