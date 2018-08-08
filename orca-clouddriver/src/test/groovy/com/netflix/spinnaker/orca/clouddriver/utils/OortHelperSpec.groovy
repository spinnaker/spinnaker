/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static java.net.HttpURLConnection.HTTP_NOT_FOUND

class OortHelperSpec extends Specification {
  @Subject oortHelper = Spy(OortHelper)
  OortService oortService = Mock(OortService)

  def setup() {
    oortHelper.oortService = oortService
    oortHelper.objectMapper = new ObjectMapper()
  }

  def "getInstancesForCluster fails if > 1 asg in the cluster"() {
    when:
    def oortResponse = '''\
    {
      "serverGroups":[{
        "name": "myapp-v002",
        "region": "us-west-2",
        "asg": { "createdTime": 12344, "suspendedProcesses": [{"processName": "AddToLoadBalancer"}] },
        "image": { "imageId": "ami-012", "name": "ami-012" },
        "buildInfo": { "job": "foo-build", "buildNumber": 1 },
        "instances": [ { "id": 1 }, { "id": 2 } ]
      },{
        "name": "myapp-v003",
        "region":"us-west-2",
        "asg": { "createdTime": 23456,  "suspendedProcesses": [] },
        "image": { "imageId": "ami-234", "name": "ami-234" },
        "buildInfo": { "job": "foo-build", "buildNumber": 1 },
        "instances": [ { "id": 1 } ]
      }]
    }
    '''.stripIndent()
    Response response = new Response('http://oort', 200, 'OK', [], new TypedString(oortResponse))
    Map deployContext = ["region" : "us-west-2", "account" : "prod", "kato.tasks" : [[resultObjects : [[ancestorServerGroupNameByRegion: ["us-west-2" : "myapp-v000"]],[serverGroupNameByRegion : ["us-west-2" : "myapp-v002"]]]]]]
    1 * oortService.getCluster("myapp", "prod", "myapp", "aws") >> response
    oortHelper.getInstancesForCluster(deployContext, "myapp-v002", true, true)

    then:
    def e = thrown(RuntimeException)
    e.message =~ "there is more than one server group in the cluster"
  }

  def "getInstancesForCluster fails if any instances are down/starting"() {
    when:
    def oortResponse = '''\
    {
      "serverGroups":[{
        "name": "myapp-v002",
        "region": "us-west-2",
        "asg": { "createdTime": 12344, "suspendedProcesses": [{"processName": "AddToLoadBalancer"}] },
        "image": { "imageId": "ami-012", "name": "ami-012" },
        "buildInfo": { "job": "foo-build", "buildNumber": 1 },
        "instances": [ { "instanceId": 1, "health" : [{"healthCheckUrl" : "http://foo/bar"}, {"status": "DOWN"}] }, { "instanceId": 2, "health" : [{"healthCheckUrl" : "http://foo2/bar2"}, {"status": "UP"}] } ]
      }]
    }
    '''.stripIndent()
    Response response = new Response('http://oort', 200, 'OK', [], new TypedString(oortResponse))
    Map deployContext = ["region" : "us-west-2", "account" : "prod", "kato.tasks" : [[resultObjects : [[ancestorServerGroupNameByRegion: ["us-west-2" : "myapp-v000"]],[serverGroupNameByRegion : ["us-west-2" : "myapp-v002"]]]]]]
    1 * oortService.getCluster("myapp", "prod", "myapp", "aws") >> response
   oortHelper.getInstancesForCluster(deployContext, "myapp-v002", true, true)

    then:
    def e = thrown(RuntimeException)
    e.message =~ "at least one instance is DOWN or in the STARTING state, exiting"
  }

  def "getInstancesForCluster works with deploy context"() {
    when:
    def oortResponse = '''\
    {
      "serverGroups":[{
        "name": "myapp-v002",
        "region": "us-west-2",
        "asg": { "createdTime": 12344, "suspendedProcesses": [{"processName": "AddToLoadBalancer"}] },
        "image": { "imageId": "ami-012", "name": "ami-012" },
        "buildInfo": { "job": "foo-build", "buildNumber": 1 },
        "instances": [ { "instanceId": 1, "health" : [{"healthCheckUrl" : "http://foo/bar"}, {"status": "UP"}] }, { "instanceId": 2, "health" : [{"healthCheckUrl" : "http://foo2/bar2"}, {"status": "UP"}] } ]
      }]
    }
    '''.stripIndent()
    Response response = new Response('http://oort', 200, 'OK', [], new TypedString(oortResponse))
    Map deployContext = ["region" : "us-west-2", "account" : "prod", "kato.tasks" : [[resultObjects : [[ancestorServerGroupNameByRegion: ["us-west-2" : "myapp-v000"]],[serverGroupNameByRegion : ["us-west-2" : "myapp-v002"]]]]]]
    1 * oortService.getCluster("myapp", "prod", "myapp", "aws") >> response
    def result = oortHelper.getInstancesForCluster(deployContext, "myapp-v002", true, true)

    then:
    result.get(1).healthCheckUrl == "http://foo/bar"
    result.get(2).healthCheckUrl == "http://foo2/bar2"
  }

  def "getInstancesForCluster passes if any instances are down/starting and failIfAnyInstancesUnhealthy == false"() {
    when:
    def oortResponse = '''\
    {
      "serverGroups":[{
        "name": "myapp-v002",
        "region": "us-west-2",
        "asg": { "createdTime": 12344, "suspendedProcesses": [{"processName": "AddToLoadBalancer"}] },
        "image": { "imageId": "ami-012", "name": "ami-012" },
        "buildInfo": { "job": "foo-build", "buildNumber": 1 },
        "instances": [ { "instanceId": 1, "health" : [{"healthCheckUrl" : "http://foo/bar"}, {"status": "DOWN"}] },
                       { "instanceId": 2, "health" : [{"healthCheckUrl" : "http://foo2/bar2"}, {"status": "UP"}] },
                       { "instanceId": 3, "health" : [{"healthCheckUrl" : "http://foo2/bar3"}] },
                       { "instanceId": 4, "health" : [] }
                     ]
      }]
    }
    '''.stripIndent()
    Response response = new Response('http://oort', 200, 'OK', [], new TypedString(oortResponse))
    Map deployContext = ["region" : "us-west-2", "account" : "prod", "kato.tasks" : [[resultObjects : [[ancestorServerGroupNameByRegion: ["us-west-2" : "myapp-v000"]],[serverGroupNameByRegion : ["us-west-2" : "myapp-v002"]]]]]]
    1 * oortService.getCluster("myapp", "prod", "myapp", "aws") >> response
    def result = oortHelper.getInstancesForCluster(deployContext, "myapp-v002", true, false)

    then:
    result.size() == 4
  }

  @Unroll
  def "should support fetching a target server group that does not exist"() {
    when:
    def optionalTargetServerGroup = Optional.empty()
    def thrownException

    try {
      optionalTargetServerGroup = oortHelper.getTargetServerGroup("test", serverGroupName, "us-west-2", "aws")
    } catch (Exception e) {
      thrownException = e
    }

    then:
    1 * oortService.getServerGroup("test", "us-west-2", serverGroupName) >> {
      if (statusCode == 200) {
        return new Response("http://clouddriver", statusCode, "OK", [], new TypedString("""{"name": "${serverGroupName}"}"""))
      }

      throw RetrofitError.httpError(
        null,
        new Response("http://clouddriver", statusCode, "", [], null),
        null,
        null
      )
    }

    optionalTargetServerGroup.isPresent() == shouldExist
    (thrownException != null) == shouldThrowException

    where:
    serverGroupName | statusCode || shouldExist || shouldThrowException
    "app-v001"      | 200        || true        || false
    "app-v002"      | 404        || false       || false
    "app-v003"      | 500        || false       || true       // a non-404 should just rethrow the exception
  }
}
