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

import com.google.gson.Gson
import com.netflix.spinnaker.gate.config.ApplicationConfigurationProperties
import com.netflix.spinnaker.gate.config.Service
import com.netflix.spinnaker.gate.config.ServiceConfiguration
import com.netflix.spinnaker.gate.services.ApplicationService
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.converter.GsonConverter
import retrofit.mime.TypedByteArray
import spock.lang.Specification
import spock.lang.Unroll

class ApplicationServiceSpec extends Specification {
  def clouddriver = Mock(ClouddriverService)
  def front50 = Mock(Front50Service)
  def clouddriverSelector = Mock(ClouddriverServiceSelector) {
    select() >> clouddriver
  }

  private applicationService() {
    applicationService(new ApplicationConfigurationProperties())
  }

  private applicationService(ApplicationConfigurationProperties applicationConfigurationProperties) {
    def config = new ServiceConfiguration(services: [front50: new Service()])
    def service = new ApplicationService(
      config,
      clouddriverSelector,
      front50,
      applicationConfigurationProperties
    )
    return service
  }

  void setup() {
    println "--------------- Test " + specificationContext.currentIteration.name
  }

  void "should properly aggregate application data from Front50 and Clouddriver when useFront50AsSourceOfTruth: #useFront50AsSourceOfTruth"() {
    given:
    def clouddriverApp = [name: name, attributes: [clouddriverName: name, name: "bad"], clusters: [(account): [cluster]]]
    def front50App = [name: name, email: email, owner: owner, accounts: account]
    ApplicationConfigurationProperties applicationConfigurationProperties = new ApplicationConfigurationProperties()
    applicationConfigurationProperties.setUseFront50AsSourceOfTruth(useFront50AsSourceOfTruth)

    when:
    def service = applicationService(applicationConfigurationProperties)
    def app = service.getApplication(name, true)

    then:
    1 * clouddriver.getApplication(name) >> clouddriverApp
    1 * front50.getApplication(name) >> front50App

    app == [name: name, attributes: (clouddriverApp.attributes + front50App), clusters: clouddriverApp.clusters]

    where:
    useFront50AsSourceOfTruth << [true, false]
    name = "foo"
    email = "bar@baz.bz"
    owner = "danw"
    cluster = "cluster1"
    account = "test"
    providerType = "aws"
  }

  @Unroll
  void "when UseFront50AsSourceOfTruth: #checkFront50 and application exists only in clouddriver"() {
    given:
    def clouddriverApp = [name: name, attributes: [clouddriverName: name, name: "bad"], clusters: [(account): [cluster]]]
    def front50App = [:]
    ApplicationConfigurationProperties applicationConfigurationProperties = new ApplicationConfigurationProperties()
    applicationConfigurationProperties.setUseFront50AsSourceOfTruth(checkFront50)

    when:
    def service = applicationService(applicationConfigurationProperties)
    def app = service.getApplication(name, true)

    then:
    1 * front50.getApplication(name) >> front50App

    numberOfClouddriverInvocations * clouddriver.getApplication(name) >> clouddriverApp

    assert app == result

    where:
    checkFront50 | numberOfClouddriverInvocations | result
    true         | 0                              | null
    false        | 1                              | [name:"foo", attributes:[clouddriverName:"foo", name:"foo", accounts: "test"], clusters:["test":["cluster1"]]]

    name = "foo"
    email = "bar@baz.bz"
    owner = "danw"
    cluster = "cluster1"
    account = "test"
    providerType = "aws"
  }

  @Unroll
  void "when UseFront50AsSourceOfTruth: #checkFront50 and application exists only in clouddriver, but front50 throws an exception"() {
    given:
    def clouddriverApp = [name: name, attributes: [clouddriverName: name, name: "bad"], clusters: [(account): [cluster]]]
    ApplicationConfigurationProperties applicationConfigurationProperties = new ApplicationConfigurationProperties()
    applicationConfigurationProperties.setUseFront50AsSourceOfTruth(checkFront50)

    when:
    def service = applicationService(applicationConfigurationProperties)
    def app = service.getApplication(name, true)

    then:
    1 * front50.getApplication(name) >> { throw exception }

    numberOfClouddriverInvocations * clouddriver.getApplication(name) >> clouddriverApp

    assert app == result

    where:
    checkFront50 | numberOfClouddriverInvocations | result | exception
    true         | 0                              | null   | new Exception("fatal exception")
    true         | 0                              | null   | retrofit404()
    false        | 1                              | [name:"foo", attributes:[clouddriverName:"foo", name:"foo", accounts: "test"], clusters:["test":["cluster1"]]] | new Exception("fatal exception")
    false        | 1                              | [name:"foo", attributes:[clouddriverName:"foo", name:"foo", accounts: "test"], clusters:["test":["cluster1"]]] | retrofit404()

    name = "foo"
    email = "bar@baz.bz"
    owner = "danw"
    cluster = "cluster1"
    account = "test"
    providerType = "aws"
  }

  @Unroll
  void "when UseFront50AsSourceOfTruth: #checkFront50 and application exists only in front50, but clouddriver throws an exception"() {
    given:
    def front50App = [name: name, email: email, owner: owner, accounts: account]
    ApplicationConfigurationProperties applicationConfigurationProperties = new ApplicationConfigurationProperties()
    applicationConfigurationProperties.setUseFront50AsSourceOfTruth(checkFront50)

    when:
    def service = applicationService(applicationConfigurationProperties)
    def app = service.getApplication(name, true)

    then:
    1 * front50.getApplication(name) >> front50App
    1 * clouddriver.getApplication(name) >> { throw exception }

    assert app == [name: name, attributes:[name: name, email: email, owner: owner, accounts: account], clusters:[:]]

    where:
    checkFront50 | exception
    true         | new Exception("fatal exception")
    true         | retrofit404()
    false        | new Exception("fatal exception")
    false        | retrofit404()

    name = "foo"
    email = "bar@baz.bz"
    owner = "danw"
    cluster = "cluster1"
    account = "test"
    providerType = "aws"
  }

  @Unroll
  void "when UseFront50AsSourceOfTruth: #checkFront50 and both front50 and clouddriver throw an exception"() {
    given:
    ApplicationConfigurationProperties applicationConfigurationProperties = new ApplicationConfigurationProperties()
    applicationConfigurationProperties.setUseFront50AsSourceOfTruth(checkFront50)

    when:
    def service = applicationService(applicationConfigurationProperties)
    def app = service.getApplication(name, true)

    then:
    1 * front50.getApplication(name) >> { throw exception }
    numberOfClouddriverInvocations * clouddriver.getApplication(name) >> { throw exception }

    assert app == null

    where:
    checkFront50 | numberOfClouddriverInvocations | exception
    true         | 0                              | new Exception("fatal exception")
    true         | 0                              | retrofit404()
    false        | 1                              | new Exception("fatal exception")
    false        | 1                              | retrofit404()

    name = "foo"
  }

  void "should ignore accounts from front50 and only include those from clouddriver clusters when UseFront50AsSourceOfTruth: #checkFront50"() {
    given:
    def clouddriverApp = [name: name, attributes: [clouddriverName: name, name: "bad"], clusters: [(clouddriverAccount): [cluster]]]
    def front50App = [name: name, email: email, owner: owner, accounts: front50Account]
    ApplicationConfigurationProperties applicationConfigurationProperties = new ApplicationConfigurationProperties()
    applicationConfigurationProperties.setUseFront50AsSourceOfTruth(checkFront50)

    when:
    def service = applicationService()
    def app = service.getApplication(name, true)

    then:
    1 * clouddriver.getApplication(name) >> clouddriverApp
    1 * front50.getApplication(name) >> front50App

    app == [name: name, attributes: (clouddriverApp.attributes + front50App + [accounts: [clouddriverAccount].toSet().sort().join(',')]), clusters: clouddriverApp.clusters]

    where:
    checkFront50 << [true, false]
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
    def config = new ServiceConfiguration(services: [front50: new Service(config: [includedAccounts: includedAccount])])
    ApplicationService serviceWithDifferentConfig = new ApplicationService(
      config,
      clouddriverSelector,
      front50,
      new ApplicationConfigurationProperties()
    )

    when:
    def app = serviceWithDifferentConfig.getApplication(name, true)

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
    when:
    def service = applicationService()
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
    given:
    def clouddriverApp = [name: name.toUpperCase(), attributes: [name: name], clusters: [prod: [[name: "cluster-name"]]]]
    def front50App = [name: name.toLowerCase(), email: email]

    when:
    def service = applicationService()
    service.refreshApplicationsCache()
    def apps = service.getAllApplications()

    then:
    1 * clouddriver.getAllApplicationsUnrestricted(true) >> [clouddriverApp]
    1 * front50.getAllApplicationsUnrestricted() >> [front50App]

    1 == apps.size()
    apps[0].email == email
    apps[0].name == name
    apps[0].clusters == null
    apps[0].accounts == "prod"

    where:
    name = "foo"
    email = "foo@bar.bz"
  }

  void "should properly merge accounts for retrieved apps with clusterNames"() {
    given:
    def clouddriverApp1 = [name: name.toUpperCase(), attributes: [name: name], clusterNames: [prod: ["cluster-prod"]]]
    def clouddriverApp2 = [name: name.toUpperCase(), attributes: [name: name], clusterNames: [dev: ["cluster-dev"]]]
    def front50App = [name: name.toLowerCase(), email: email, accounts: "test"]

    when:
    def service = applicationService()
    service.refreshApplicationsCache()
    def apps = service.getAllApplications()

    then:
    1 * clouddriver.getAllApplicationsUnrestricted(true) >> [clouddriverApp1, clouddriverApp2]
    1 * front50.getAllApplicationsUnrestricted() >> [front50App]

    1 == apps.size()
    apps[0].email == email
    apps[0].name == name
    apps[0].clusters == null
    apps[0].accounts == "prod,dev"

    where:
    name = "foo"
    email = "foo@bar.bz"
  }

  @Unroll
  def "should properly merge accounts for retrieved apps with clusterNames when useFront50AsSourceOfTruth is #checkFront50"() {
    given:
    def clouddriverApp1 = [name: "appname1", attributes: [name: "appname1"], clusterNames: [prod: ["cluster-prod"]]]
    def clouddriverApp2 = [name: "appname2", attributes: [name: "appname2"], clusterNames: [dev: ["cluster-dev"]]]
    def front50App = [name: "appname1", email:  "foo@bar.bz", accounts: "test"]
    ApplicationConfigurationProperties applicationConfigurationProperties = new ApplicationConfigurationProperties()
    applicationConfigurationProperties.setUseFront50AsSourceOfTruth(checkFront50)

    when:
    def service = applicationService(applicationConfigurationProperties)
    service.refreshApplicationsCache()
    def apps = service.getAllApplications()

    then:
    1 * clouddriver.getAllApplicationsUnrestricted(true) >> [clouddriverApp1, clouddriverApp2]
    1 * front50.getAllApplicationsUnrestricted() >> [front50App]

    assert apps.size() == resultSize
    assert apps == result

    where:
    checkFront50 | resultSize | result
    true         | 1          | [[name:"appname1", email:"foo@bar.bz", accounts:"prod"]]
    false        | 2          | [[name:"appname1", email:"foo@bar.bz", accounts:"prod"], [name:"appname2", accounts:"dev"]]
  }

  @Unroll
  def "should handle front50 returning an exception when useFront50AsSourceOfTruth is #checkFront50"() {
    given:
    def clouddriverApp1 = [name: "appname1", attributes: [name: "appname1"], clusterNames: [prod: ["cluster-prod"]]]
    def clouddriverApp2 = [name: "appname2", attributes: [name: "appname2"], clusterNames: [dev: ["cluster-dev"]]]
    ApplicationConfigurationProperties applicationConfigurationProperties = new ApplicationConfigurationProperties()
    applicationConfigurationProperties.setUseFront50AsSourceOfTruth(checkFront50)

    when:
    def service = applicationService(applicationConfigurationProperties)
    service.refreshApplicationsCache()
    def apps = service.getAllApplications()

    then:
    1 * front50.getAllApplicationsUnrestricted() >> { throw exception }
    1 * clouddriver.getAllApplicationsUnrestricted(true) >> [clouddriverApp1, clouddriverApp2]

    assert apps.size() == resultSize
    assert apps == result

    where:
    checkFront50 | resultSize | result                                                                  | exception
    true         | 0          | []                                                                      | new Exception("fatal exception")
    true         | 0          | []                                                                      | retrofit404()
    false        | 2          | [[name:"appname1", accounts:"prod"], [name:"appname2", accounts:"dev"]] | new Exception("fatal exception")
    false        | 2          | [[name:"appname1", accounts:"prod"], [name:"appname2", accounts:"dev"]] | retrofit404()
  }

  @Unroll
  def "should handle clouddriver returning an exception when useFront50AsSourceOfTruth is #checkFront50"() {
    given:
    def front50App = [name: name, email:  email, accounts: account]
    ApplicationConfigurationProperties applicationConfigurationProperties = new ApplicationConfigurationProperties()
    applicationConfigurationProperties.setUseFront50AsSourceOfTruth(checkFront50)

    when:
    def service = applicationService(applicationConfigurationProperties)
    service.refreshApplicationsCache()
    def apps = service.getAllApplications()

    then:
    1 * front50.getAllApplicationsUnrestricted() >> [front50App]
    1 * clouddriver.getAllApplicationsUnrestricted(true) >> { throw exception }

    assert apps.size() == 1
    assert apps == [[name: name, email: email, accounts: account]]

    where:
    checkFront50 | exception
    true         | new Exception("fatal exception")
    true         | retrofit404()
    false        | new Exception("fatal exception")
    false        | retrofit404()

    name = "appname1"
    email = "foo@bar.bz"
    account = "test"
  }

  @Unroll
  def "should handle both front50 and clouddriver returning an exception when useFront50AsSourceOfTruth is #checkFront50"() {
    given:
    ApplicationConfigurationProperties applicationConfigurationProperties = new ApplicationConfigurationProperties()
    applicationConfigurationProperties.setUseFront50AsSourceOfTruth(checkFront50)

    when:
    def service = applicationService(applicationConfigurationProperties)
    service.refreshApplicationsCache()
    def apps = service.getAllApplications()

    then:
    1 * front50.getAllApplicationsUnrestricted() >> { throw exception }
    1 * clouddriver.getAllApplicationsUnrestricted(true) >> { throw exception }

    assert apps.size() == 0

    where:

    checkFront50 | exception
    true         | new Exception("fatal exception")
    true         | retrofit404()
    false        | new Exception("fatal exception")
    false        | retrofit404()

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

  void "getPipelineConfigForApplication returns pipeline config based on name"() {
    given:
    def app = "theApp"
    def nameOrId = "by-name"

    when:
    def service = applicationService()
    def result = service.getPipelineConfigForApplication(app, nameOrId)

    then:
    result != null
    1 * front50.getPipelineConfigByApplicationAndName(app, nameOrId, true) >> [ id: "by-id", name: "by-name" ]
    0 * front50.getPipelineConfigById(_)
  }

  void "getPipelineConfigForApplication returns pipeline config based on id"() {
    given:
    def app = "theApp"
    def nameOrId = "by-id"

    when:
    def service = applicationService()
    def result = service.getPipelineConfigForApplication(app, nameOrId) != null

    then:
    result != null
    1 * front50.getPipelineConfigByApplicationAndName(app, nameOrId, true) >> { throw retrofit404() }
    1 * front50.getPipelineConfigById(nameOrId) >> [ id: "by-id", name: "by-name" ]
  }

  void "getPipelineConfigForApplication throws an exception when neither name nor id match"() {
    given:
    def app = "theApp"
    def nameOrId = "not-id"

    when:
    def service = applicationService()
    service.getPipelineConfigForApplication(app, nameOrId)

    then:
    def e = thrown SpinnakerHttpException
    e.responseCode == 404
    1 * front50.getPipelineConfigByApplicationAndName(app, nameOrId, true) >> { throw retrofit404() }
    1 * front50.getPipelineConfigById(nameOrId) >> { throw retrofit404() }
  }

  void "getPipelineConfigForApplication queries by id when response to query by name doesn't match"() {
    given:
    def app = "theApp"
    def nameOrId = "by-name"

    when:
    def service = applicationService()
    def result = service.getPipelineConfigForApplication(app, nameOrId)

    then:
    result != null
    1 * front50.getPipelineConfigByApplicationAndName(app, nameOrId, true) >> [ id: "arbitrary-id", name: "some-other-name" ]

    // The key part of this test is that gate queries front50 by id.  The choice
    // of id here needs to match the expectation for result (i.e. throw an exception or not),
    // but is otherwise arbitrary.
    1 * front50.getPipelineConfigById(nameOrId) >> [ id: nameOrId, name: "arbitrary-name" ]
  }

  void "getPipelineConfigForApplication throws an exception when response to query by id doesn't match"() {
    given:
    def app = "theApp"
    def nameOrId = "by-id"

    when:
    def service = applicationService()
    service.getPipelineConfigForApplication(app, nameOrId)

    then:
    thrown NotFoundException
    1 * front50.getPipelineConfigByApplicationAndName(app, nameOrId, true) >> { throw retrofit404() }
    1 * front50.getPipelineConfigById(nameOrId) >> [ id: "some-other-id", name: "arbitrary-name" ]
  }

  void "should skip clouddriver call if expand set to false"() {
    given:
    def name = 'myApp'
    def serviceWithApplicationsCache = applicationService()
    serviceWithApplicationsCache.getAllApplicationsCache().set([
        [name: name, email: "cached@email.com"]
    ])

    when:
    def app = serviceWithApplicationsCache.getApplication(name, false)

    then:
    0 * clouddriver.getApplication(name)
    1 * front50.getApplication(name) >> {
      return [
          name: name,
          email: "updated@email.com"
      ]
    }

    app.attributes.email == "updated@email.com"
  }

  def retrofit404(){
    RetrofitError.httpError("http://localhost", new Response("http://localhost", 404, "Not Found", [], new TypedByteArray("application/json", new byte[0])), new GsonConverter(new Gson()), Map)
  }
}
