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
import com.netflix.spinnaker.orca.oort.InstanceService
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Shared
import spock.lang.Unroll

class JarDiffsTaskSpec extends Specification {
  @Subject task = Spy(JarDiffsTask)

  InstanceService instanceService = Mock(InstanceService)

  def setup() {
    task.objectMapper = new ObjectMapper()
  }

  Map deployContext = ["kato.tasks" : [[resultObjects : [[ancestorServerGroupNameByRegion: ["us-east-1" : "myapp-v000"]],[serverGroupNameByRegion : ["us-east-1" : "myapp-v002"]]]]]]

  void "getTargetAsg with deploy context"() {
    when:
      def result = task.getTargetAsg(deployContext, "us-east-1")

    then:
      result == "myapp-v002"
  }

  void "getSourceAsg with deploy context"() {
    when:
    def result = task.getSourceAsg(deployContext, "us-east-1")

    then:
    result == "myapp-v000"
  }

  void "diffJars"() {
    given:
    def source = [jars: [[name: "/apps/tomcat/webapps/ROOT/WEB-INF/lib/base-explorer-2.1423.0.jar", implementationVersion: "2.1386.3.7", implementationTitle: "netflix#base-explorer;2.1386.3.7"],
                         [name: "/apps/tomcat/webapps/ROOT/WEB-INF/lib/account-common-55.87.jar", implementationVersion: "55.87", implementationTitle: "netflix#account-common;55.87"],
                         [name: "/apps/tomcat/webapps/ROOT/WEB-INF/lib/astyanax-3.6.0.jar", implementationVersion: "3.6.0", implementationTitle: "com.netflix.astyanax#astyanax;3.6.0"],
                         [name: "/apps/tomcat/webapps/ROOT/WEB-INF/lib/cassandra-thrift-2.0.12.jar", implementationVersion: "2.0.12", implementationTitle: "Cassandra"]
    ]
    ]
    def target = [jars: [[name: "/apps/apache-tomcat-7.0.59/lib/tomcat-util.jar", implementationVersion: "7.0.59", implementationTitle: "Apache Tomcat"],
                         [name: "/apps/tomcat/webapps/ROOT/WEB-INF/lib/account-common-55.87.jar", implementationVersion: "55.87", implementationTitle: "netflix#account-common;55.87"],
                         [name: "/apps/tomcat/webapps/ROOT/WEB-INF/lib/astyanax-3.6.0.jar", implementationVersion: "3.6.0", implementationTitle: "com.netflix.astyanax#astyanax;3.6.0"],
                         [name: "/apps/tomcat/webapps/ROOT/WEB-INF/lib/cassandra-thrift-2.0.11.jar", implementationVersion: "2.0.11", implementationTitle: "Cassandra"]
    ]
    ]

    when:
    def result = task.diffJars(source, target)
    println result.dump()

    then:
    result.source.name == ["/apps/tomcat/webapps/ROOT/WEB-INF/lib/base-explorer-2.1423.0.jar", "/apps/tomcat/webapps/ROOT/WEB-INF/lib/cassandra-thrift-2.0.12.jar"]
    result.target.name == ["/apps/apache-tomcat-7.0.59/lib/tomcat-util.jar", "/apps/tomcat/webapps/ROOT/WEB-INF/lib/cassandra-thrift-2.0.11.jar"]
  }

  void "getJarList single server"() {
    given:
    1 * task.createInstanceService(_) >> instanceService

    def sourceJarsResponse = "{\"jars\":[{\"id\":0,\"name\":\"/apps/jre/lib/1.6.0/ext/IngrianLog4j.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"-\",\"implementationTitle\":\"-\",\"specificationVersion\":\"-\"},{\"id\":1,\"name\":\"/usr/lib/jvm/java-7-oracle-1.7.0.80/jre/lib/ext/IngrianNAE-5.4.0.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"5.4.0\",\"implementationTitle\":\"Ingrian Provider 5.4.0.000006\",\"specificationVersion\":\"-\"},{\"id\":2,\"name\":\"/apps/apache-tomcat-7.0.59/bin/tomcat-juli.jar\",\"libraryOwner\":\"-\",\"buildDate\":\"-\",\"status\":\"-\",\"implementationVersion\":\"7.0.59\",\"implementationTitle\":\"Apache Tomcat\",\"specificationVersion\":\"7.0\"}]}"
    Response jarsResponse = new Response('http://foo.com', 200, 'OK', [], new TypedString(sourceJarsResponse))

    when:
    1 * instanceService.getJars() >> jarsResponse
    def result = task.getJarList([foo: [hostName : "bar"]])
    println result.dump()

    then:
    result.jars.size() == 3
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
    println result.dump()

    then:
    result.jars.size() == 3
  }
}
