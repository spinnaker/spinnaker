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
    def front50App = [name: name, email: email, owner: owner, accounts: account]

    when:
    def app = service.getApplication(name, true)

    then:
    1 * clouddriver.getApplication(name) >> clouddriverApp
    1 * front50.getApplication(name) >> front50App

    app == [name: name, attributes: (clouddriverApp.attributes + front50App), clusters: clouddriverApp.clusters]

    where:
    name = "foo"
    email = "bar@baz.bz"
    owner = "danw"
    cluster = "cluster1"
    account = "test"
    providerType = "aws"
  }

  void "should ignore accounts from front50 and only include those from clouddriver clusters"() {
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
    def front50App = [name: name, email: email, owner: owner, accounts: front50Account]

    when:
    def app = service.getApplication(name, true)

    then:
    1 * clouddriver.getApplication(name) >> clouddriverApp
    1 * front50.getApplication(name) >> front50App

    app == [name: name, attributes: (clouddriverApp.attributes + front50App + [accounts: [clouddriverAccount].toSet().sort().join(',')]), clusters: clouddriverApp.clusters]

    where:
    name = "foo"
    email = "bar@baz.bz"
    owner = "danw"
    cluster = "cluster1"
    clouddriverAccount = "test"
    front50Account = "prod"
    providerType = "aws"

  }

  @Unroll
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
    def app = service.getApplication(name, true)

    then:
    1 * clouddriver.getApplication(name) >> null
    1 * front50.getApplication(name) >> [name: name, foo: 'bar', accounts: account]

    (app == null) == expectedNull

    where:
    account     | includedAccount | expectedNull
    "prod"      | "test"          | true
    "prod"      | "test,dev"      | true
    "prod,test" | "prod,test"     | false
    "test"      | "test"          | false
    "prod"      | "prod,test"     | false
    "test"      | null            | false
    "test"      | ""              | false

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
    def app = service.getApplication(name, true)

    then:
    1 * clouddriver.getApplication(name) >> null
    1 * front50.getApplication(name) >> null

    app == null

    where:
    name = "foo"
    account = "test"
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
    1 * front50.getAllApplications() >> [front50App] >> { throw new SocketTimeoutException() }

    1 == apps.size()
    service.allApplicationsCache.set(apps)
    apps[0].email == email
    apps[0].name == name
    apps[0].clusters == null

    when: "should return last known good values if an exception is thrown"
    def allApps = service.getAllApplications()
    def singleApp = service.getApplication(name, true)

    then:
    1 * front50.getApplication(name) >> { throw new SocketTimeoutException() }
    1 * front50.getAllApplications() >> { throw new SocketTimeoutException() }

    1 == allApps.size()
    singleApp.name == allApps[0].name

    where:
    name = "foo"
    email = "foo@bar.bz"
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

  @Unroll
  void "should return pipeline config based on name or id"() {
    given:
    HystrixRequestContext.initializeContext()

    def service = new ApplicationService()
    def front50 = Mock(Front50Service)
    def clouddriver = Mock(ClouddriverService)
    def config = new ServiceConfiguration(services: [front50: new Service()])

    service.serviceConfiguration = config
    service.front50Service = front50
    service.clouddriverService = clouddriver
    service.executorService = Executors.newFixedThreadPool(1)
    def app = "theApp"

    when:
    def result = service.getPipelineConfigForApplication(app, nameOrId) != null

    then:
    result == expected
    1 * front50.getPipelineConfigsForApplication(app) >> [ [ id: "by-id", name: "by-name" ] ]

    where:
    nameOrId  || expected
    "by-id"   || true
    "by-name" || true
    "not-id"  || false
  }

  void "should skip clouddriver call if expand set to false"() {
    setup:
    HystrixRequestContext.initializeContext()

    def service = new ApplicationService()
    def front50 = Mock(Front50Service)
    def clouddriver = Mock(ClouddriverService)
    def config = new ServiceConfiguration(services: [front50: new Service()])
    def name = 'myApp'

    service.serviceConfiguration = config
    service.front50Service = front50
    service.clouddriverService = clouddriver
    service.executorService = Executors.newFixedThreadPool(1)

    when:
    def app = service.getApplication(name, false)

    then:
    0 * clouddriver.getApplication(name)
    1 * front50.getApplication(name) >> null
  }
}
