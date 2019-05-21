/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.eureka.deploy.ops

import com.netflix.spinnaker.clouddriver.eureka.api.Eureka
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class AbstractEurekaSupportSpec extends Specification {
  def clusterProvider = Mock(ClusterProvider)

  @Subject
  def eurekaSupport = new MyEurekaSupport(clusterProviders: [clusterProvider])

  @Unroll
  def "identifies up instances to disable"() {
    when:
    def instancesToModify = eurekaSupport.getInstanceToModify("test", "us-west-2", "asg-v001", allInstances, percentageToDisable)

    then:
    1 * clusterProvider.getServerGroup("test", "us-west-2", "asg-v001") >> {
      return serverGroup(
        instancesInServerGroup.collect {
          instance(it.key, [["state": it.value]])
        }
      )
    }

    instancesToModify == expectedInstancesToModify

    where:
    allInstances                        | instancesInServerGroup                                            | percentageToDisable || expectedInstancesToModify
    ["i-1", "i-2"]                      | ["i-1": "UP"]                                                     | 50                  || ["i-1"]                // i-2 doesn't actually exist so should be skipped
    ["i-1", "i-2"]                      | ["i-1": "OUT_OF_SERVICE"]                                         | 50                  || []                     // i-1 is already disabled
    ["i-1", "i-2"]                      | ["i-1": "UP"]                                                     | 1                   || ["i-1"]                // always round up when determining what to disable
    ["i-1", "i-2", "i-3"]               | ["i-1": "UP", "i-2": "UP", "i-3": "UP"]                           | 100                 || ["i-1", "i-2", "i-3"]
    ["i-1", "i-2", "i-3"]               | ["i-1": "UP", "i-2": "UP", "i-3": "UP"]                           | 30                  || ["i-1"]
    ["i-1", "i-2", "i-3"]               | ["i-1": "UP", "i-2": "UP", "i-3": "UP"]                           | 60                  || ["i-1", "i-2"]
    ["i-1", "i-2", "i-3"]               | ["i-1": "UP", "i-2": "UP", "i-3": "UP"]                           | 90                  || ["i-1", "i-2", "i-3"]
    ["i-1", "i-2", "i-3"]               | ["i-1": "UP", "i-2": "UP", "i-3": "UP"]                           | 90                  || ["i-1", "i-2", "i-3"]
    ["i-1", "i-2", "i-3", "i-4", "i-5"] | ["i-1": "UP", "i-2": "UP", "i-3": "UP", "i-4": "UP", "i-5": "UP"] | 20                  || ["i-1"]
  }

  ServerGroup serverGroup(List<Instance> instances) {
    return Mock(ServerGroup) {
      _ * getInstances() >> { return instances }
    }
  }

  Instance instance(String name, List<Map<String, String>> healths) {
    return Mock(Instance) {
      _ * getName() >> { return name }
      _ * getHealth() >> { return healths }
    }
  }

  class MyEurekaSupport extends AbstractEurekaSupport {
    @Override
    Eureka getEureka(Object credentials, String region) {
      throw new UnsupportedOperationException()
    }

    @Override
    boolean verifyInstanceAndAsgExist(Object credentials, String region, String instanceId, String asgName) {
      throw new UnsupportedOperationException()
    }
  }
}


