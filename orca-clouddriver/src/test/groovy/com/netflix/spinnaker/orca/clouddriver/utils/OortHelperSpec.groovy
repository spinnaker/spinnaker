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

import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.model.Cluster
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import spock.lang.Specification
import spock.lang.Subject

class OortHelperSpec extends Specification {
  CloudDriverService cloudDriverService = Mock()
  @Subject oortHelper = new OortHelper(cloudDriverService)

  def "getInstancesForCluster fails if > 1 asg in the cluster"() {
    given:
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
    Map deployContext = ["region" : "us-west-2", "account" : "prod", "kato.tasks" : [[resultObjects : [[ancestorServerGroupNameByRegion: ["us-west-2" : "myapp-v000"]],[serverGroupNameByRegion : ["us-west-2" : "myapp-v002"]]]]]]
    1 * cloudDriverService.getCluster("myapp", "prod", "myapp", "aws") >> cluster(oortResponse)

    when:
    oortHelper.getInstancesForCluster(deployContext, "myapp-v002", true)

    then:
    def e = thrown(RuntimeException)
    e.message =~ "there is more than one server group in the cluster"
  }

  def "getInstancesForCluster works with deploy context"() {
    given:
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
    Map deployContext = ["region" : "us-west-2", "account" : "prod", "kato.tasks" : [[resultObjects : [[ancestorServerGroupNameByRegion: ["us-west-2" : "myapp-v000"]],[serverGroupNameByRegion : ["us-west-2" : "myapp-v002"]]]]]]
    1 * cloudDriverService.getCluster("myapp", "prod", "myapp", "aws") >> cluster(oortResponse)

    when:
    def result = oortHelper.getInstancesForCluster(deployContext, "myapp-v002", true)

    then:
    result.get('1').healthCheckUrl == "http://foo/bar"
    result.get('2').healthCheckUrl == "http://foo2/bar2"
  }

  def "getInstancesForCluster passes if any instances are down/starting and failIfAnyInstancesUnhealthy == false"() {
    given:
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
    Map deployContext = ["region" : "us-west-2", "account" : "prod", "kato.tasks" : [[resultObjects : [[ancestorServerGroupNameByRegion: ["us-west-2" : "myapp-v000"]],[serverGroupNameByRegion : ["us-west-2" : "myapp-v002"]]]]]]
    1 * cloudDriverService.getCluster("myapp", "prod", "myapp", "aws") >> cluster(oortResponse)

    when:
    def result = oortHelper.getInstancesForCluster(deployContext, "myapp-v002", true)

    then:
    result.size() == 4
  }

  static Cluster cluster(String json) {
    OrcaObjectMapper.instance.readValue(json, Cluster)
  }
}
