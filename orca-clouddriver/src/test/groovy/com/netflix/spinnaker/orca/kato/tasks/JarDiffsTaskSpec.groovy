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

package com.netflix.spinnaker.orca.kato.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.InstanceService
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.libdiffs.DefaultComparableLooseVersion
import com.netflix.spinnaker.orca.libdiffs.Library
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline

class JarDiffsTaskSpec extends Specification {
  @Subject task = Spy(JarDiffsTask)

  def oortHelper = Mock(OortHelper)

  InstanceService instanceService = Mock(InstanceService)

  @Shared
  Execution pipeline = Execution.newPipeline("orca")

  def setup() {
    GroovyMock(OortHelper, global: true)
    task.objectMapper = new ObjectMapper()
    task.comparableLooseVersion = new DefaultComparableLooseVersion()
  }

  Map deployContext = ["availabilityZones" : ["us-west-2": ["us-west-2a"]],"kato.tasks" : [[resultObjects : [[ancestorServerGroupNameByRegion: ["us-west-2" : "myapp-v000"]],[serverGroupNameByRegion : ["us-west-2" : "myapp-v002"]]]]]]

  void "getTargetAsg with deploy context"() {
    when:
      def result = task.getTargetAsg(deployContext, "us-west-2")

    then:
      result == "myapp-v002"
  }

  void "getSourceAsg with deploy context"() {
    when:
    def result = task.getSourceAsg(deployContext, "us-west-2")

    then:
    result == "myapp-v000"
  }

  void "getTargetAsg with canary context"() {
  }

  void "getSourceAsg with canary context"() {
  }

  void "getJarList single server"() {
    given:
    1 * task.createInstanceService(_) >> instanceService

    def sourceJarsResponse = "{\"jars\":[{\"id\":0,\"name\":\"/apps/jre/lib/1.6.0/ext/IngrianLog4j.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"-\",\"implementationTitle\":\"-\",\"specificationVersion\":\"-\"},{\"id\":1,\"name\":\"/usr/lib/jvm/java-7-oracle-1.7.0.80/jre/lib/ext/IngrianNAE-5.4.0.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"5.4.0\",\"implementationTitle\":\"Ingrian Provider 5.4.0.000006\",\"specificationVersion\":\"-\"},{\"id\":2,\"name\":\"/apps/apache-tomcat-7.0.59/bin/tomcat-juli.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"7.0.59\",\"implementationTitle\":\"Apache Tomcat\",\"specificationVersion\":\"7.0\"}]}"
    Response jarsResponse = new Response('http://foo.com', 200, 'OK', [], new TypedString(sourceJarsResponse))

    when:
    1 * instanceService.getJars() >> jarsResponse
    def result = task.getJarList([foo: [hostName : "bar"]])

    then:
    result.size() == 3

    Library lib1 = new Library("/apps/jre/lib/1.6.0/ext/IngrianLog4j.jar", "IngrianLog4j", "-", "-", "-")
    Library lib2 = new Library("/usr/lib/jvm/java-7-oracle-1.7.0.80/jre/lib/ext/IngrianNAE-5.4.0.jar", "IngrianNAE", "5.4.0", "Ingrian Provider 5.4.0.000006", "-")
    Library lib3 = new Library("/apps/apache-tomcat-7.0.59/bin/tomcat-juli.jar", "tomcat-juli", "7.0.59", "Apache Tomcat", "-")
    result == [ lib1, lib2, lib3 ]
  }

  void "getJarList first server fails, second succeeds"() {
    given:
    1 * task.createInstanceService("http://bar:8077") >> instanceService
    1 * task.createInstanceService("http://bar2:8077") >> instanceService

    def sourceJarsResponse = "{\"jars\":[{\"id\":0,\"name\":\"/apps/jre/lib/1.6.0/ext/IngrianLog4j.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"-\",\"implementationTitle\":\"-\",\"specificationVersion\":\"-\"},{\"id\":1,\"name\":\"/usr/lib/jvm/java-7-oracle-1.7.0.80/jre/lib/ext/IngrianNAE-5.4.0.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"5.4.0\",\"implementationTitle\":\"Ingrian Provider 5.4.0.000006\",\"specificationVersion\":\"-\"},{\"id\":2,\"name\":\"/apps/apache-tomcat-7.0.59/bin/tomcat-juli.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"7.0.59\",\"implementationTitle\":\"Apache Tomcat\",\"specificationVersion\":\"7.0\"}]}"
    Response jarsResponse = new Response('http://foo.com', 200, 'OK', [], new TypedString(sourceJarsResponse))

    when:
    1 * instanceService.getJars() >> {throw new RetrofitError(null, null, null, null, null, null, null)}
    1 * instanceService.getJars() >> jarsResponse
    def result = task.getJarList([foo: [hostName : "bar"], foo2: [hostName : "bar2"]])

    then:
    result.size() == 3

    Library lib1 = new Library("/apps/jre/lib/1.6.0/ext/IngrianLog4j.jar", "IngrianLog4j", "-", "-", "-")
    Library lib2 = new Library("/usr/lib/jvm/java-7-oracle-1.7.0.80/jre/lib/ext/IngrianNAE-5.4.0.jar", "IngrianNAE", "5.4.0", "Ingrian Provider 5.4.0.000006", "-")
    Library lib3 = new Library("/apps/apache-tomcat-7.0.59/bin/tomcat-juli.jar", "tomcat-juli", "7.0.59", "Apache Tomcat", "-")
    result == [ lib1, lib2, lib3 ]
  }

  void "getJarList will short circuit after 5 attempts"() {
    given:
    5 * task.createInstanceService("http://bar:8077") >> instanceService

    when:
    5 * instanceService.getJars() >> {throw new RetrofitError(null, null, null, null, null, null, null)}
    def result = task.getJarList((1..5).collectEntries { ["${it}": [hostName : "bar"]] })

    then:
    result.isEmpty()
  }

  void "getJarList stops when finds a jar list"() {
    given:
    1 * task.createInstanceService("http://bar:8077") >> instanceService
    1 * task.createInstanceService("http://bar2:8077") >> instanceService
    0 * task.createInstanceService("http://bar3:8077")

    def sourceJarsResponse = "{\"jars\":[{\"id\":0,\"name\":\"/apps/jre/lib/1.6.0/ext/IngrianLog4j.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"-\",\"implementationTitle\":\"-\",\"specificationVersion\":\"-\"},{\"id\":1,\"name\":\"/usr/lib/jvm/java-7-oracle-1.7.0.80/jre/lib/ext/IngrianNAE-5.4.0.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"5.4.0\",\"implementationTitle\":\"Ingrian Provider 5.4.0.000006\",\"specificationVersion\":\"-\"},{\"id\":2,\"name\":\"/apps/apache-tomcat-7.0.59/bin/tomcat-juli.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"7.0.59\",\"implementationTitle\":\"Apache Tomcat\",\"specificationVersion\":\"7.0\"}]}"
    Response jarsResponse = new Response('http://foo.com', 200, 'OK', [], new TypedString(sourceJarsResponse))

    when:
    1 * instanceService.getJars() >> {throw new RetrofitError(null, null, null, null, null, null, null)}
    1 * instanceService.getJars() >> jarsResponse
    def result = task.getJarList([foo: [hostName : "bar"], foo2: [hostName : "bar2"], foo3: [hostName : "bar3"]])

    then:
    result.size() == 3

    Library lib1 = new Library("/apps/jre/lib/1.6.0/ext/IngrianLog4j.jar", "IngrianLog4j", "-", "-", "-")
    Library lib2 = new Library("/usr/lib/jvm/java-7-oracle-1.7.0.80/jre/lib/ext/IngrianNAE-5.4.0.jar", "IngrianNAE", "5.4.0", "Ingrian Provider 5.4.0.000006", "-")
    Library lib3 = new Library("/apps/apache-tomcat-7.0.59/bin/tomcat-juli.jar", "tomcat-juli", "7.0.59", "Apache Tomcat", "-")
    result == [ lib1, lib2, lib3 ]
  }


  def "successfully run"() {
    given:
    2 * task.createInstanceService(_) >> instanceService
    def sourceJarsResponse = "{\"jars\":[{\"id\":0,\"name\":\"/apps/jre/lib/1.6.0/ext/IngrianLog4j.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"-\",\"implementationTitle\":\"-\",\"specificationVersion\":\"-\"},{\"id\":1,\"name\":\"/usr/lib/jvm/java-7-oracle-1.7.0.80/jre/lib/ext/IngrianNAE-5.4.0.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"5.4.0\",\"implementationTitle\":\"Ingrian Provider 5.4.0.000006\",\"specificationVersion\":\"-\"},{\"id\":2,\"name\":\"/apps/apache-tomcat-7.0.59/bin/tomcat-juli.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"7.0.59\",\"implementationTitle\":\"Apache Tomcat\",\"specificationVersion\":\"7.0\"}]}"
    def targetJarsResponse = "{\"jars\":[{\"id\":0,\"name\":\"/apps/jre/lib/1.6.0/ext/IngrianLog4j.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"-\",\"implementationTitle\":\"-\",\"specificationVersion\":\"-\"},{\"id\":1,\"name\":\"/usr/lib/jvm/java-7-oracle-1.7.0.80/jre/lib/ext/IngrianNAE-5.5.0.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"5.4.0\",\"implementationTitle\":\"Ingrian Provider 5.4.0.000006\",\"specificationVersion\":\"-\"},{\"id\":2,\"name\":\"/apps/apache-tomcat-7.0.59/bin/tomcat-juli.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"7.0.59\",\"implementationTitle\":\"Apache Tomcat\",\"specificationVersion\":\"7.0\"}]}"
    Response sourceResponse = new Response('http://foo.com', 200, 'OK', [], new TypedString(sourceJarsResponse))
    Response targetResponse = new Response('http://foo.com', 200, 'OK', [], new TypedString(targetJarsResponse))

    def config = [
      "account": "test",
      "region" : "us-west-2",
      "application": "app"
    ]

    def pipe = pipeline {
      application = "app"
    }
    def stage = new Stage(pipe, 'jarDiff', config)

    stage.context << deployContext

    when:
    task.oortHelper = oortHelper
    1 * oortHelper.getInstancesForCluster(stage.context, "myapp-v000", false, false) >> sourceExpectedInstances
    1 * oortHelper.getInstancesForCluster(stage.context, "myapp-v002", false, false) >> targetExpectedInstances
    1 * instanceService.getJars() >> sourceResponse
    1 * instanceService.getJars() >> targetResponse

    TaskResult result = task.execute(stage)

    then:
    result.context.jarDiffs.downgraded.size() == 1

    where:
    sourceExpectedInstances = ["i-1234" : [hostName : "foo.com", healthCheckUrl : "http://foo.com:7001/healthCheck"]]
    targetExpectedInstances = ["i-2345" : [hostName : "foo.com", healthCheckUrl : "http://foo.com:7001/healthCheck"]]
  }

  def "succeeds when there is an exception"() {
    given:
    2 * task.createInstanceService(_) >> instanceService
    def sourceJarsResponse = "{\"jars\":[{\"id\":0,\"name\":\"/apps/jre/lib/1.6.0/ext/IngrianLog4j.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"-\",\"implementationTitle\":\"-\",\"specificationVersion\":\"-\"},{\"id\":1,\"name\":\"/usr/lib/jvm/java-7-oracle-1.7.0.80/jre/lib/ext/IngrianNAE-5.4.0.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"5.4.0\",\"implementationTitle\":\"Ingrian Provider 5.4.0.000006\",\"specificationVersion\":\"-\"},{\"id\":2,\"name\":\"/apps/apache-tomcat-7.0.59/bin/tomcat-juli.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"7.0.59\",\"implementationTitle\":\"Apache Tomcat\",\"specificationVersion\":\"7.0\"}]}"
    def targetJarsResponse = "{\"jars\":[{\"id\":0,\"name\":\"/apps/jre/lib/1.6.0/ext/IngrianLog4j.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"-\",\"implementationTitle\":\"-\",\"specificationVersion\":\"-\"},{\"id\":1,\"name\":\"/usr/lib/jvm/java-7-oracle-1.7.0.80/jre/lib/ext/IngrianNAE-5.5.0.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"5.4.0\",\"implementationTitle\":\"Ingrian Provider 5.4.0.000006\",\"specificationVersion\":\"-\"},{\"id\":2,\"name\":\"/apps/apache-tomcat-7.0.59/bin/tomcat-juli.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"7.0.59\",\"implementationTitle\":\"Apache Tomcat\",\"specificationVersion\":\"7.0\"}]}"
    Response sourceResponse = new Response('http://foo.com', 200, 'OK', [], new TypedString(sourceJarsResponse))
    Response targetResponse = new Response('http://foo.com', 200, 'OK', [], new TypedString(targetJarsResponse))

    def config = [
      "account": "test",
      "region" : "us-west-2",
      "application": "app"
    ]

    def pipe = pipeline {
      application = "app"
    }
    def stage = new Stage(pipe, 'jarDiff', config)

    stage.context << deployContext

    when:
    task.oortHelper = oortHelper
    1 * oortHelper.getInstancesForCluster(stage.context, "myapp-v000", false, false) >> sourceExpectedInstances
    1 * oortHelper.getInstancesForCluster(stage.context, "myapp-v002", false, false) >> targetExpectedInstances
    1 * instanceService.getJars() >> {throw new RetrofitError(null, null, null, null, null, null, null)}
    1 * instanceService.getJars() >> targetResponse

    TaskResult result = task.execute(stage)

    then:
    with(result.context.jarDiffs) {
      downgraded == []
      duplicates == []
      removed    == []
      upgraded   == []
      added      == []
      unknown    == []
    }

    where:
    sourceExpectedInstances = ["i-1234" : [hostName : "foo.com", healthCheckUrl : "http://foo.com:7001/healthCheck"]]
    targetExpectedInstances = ["i-2345" : [hostName : "foo.com", healthCheckUrl : "http://foo.com:7001/healthCheck"]]
  }

  def "return success if retries limit hit"() {
    given:
    def stage = new Stage(pipeline, "jarDiffs", [jarDiffsRetriesRemaining: 0])

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
  }
}
