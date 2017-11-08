/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.flex.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.flex.FlexService
import com.netflix.spinnaker.orca.flex.model.ElasticIpResult
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification

class AssociateElasticIpTaskSpec extends Specification {
  def "should delegate Elastic Ip association to `flexService` and include result in outputs"() {
    given:
    def flexService = Mock(FlexService) {
      1 * associateElasticIp(application, account, cluster, region, _) >> { application, account, cluster, region, elasticIp ->
        assert elasticIp.type == elasticIpType
        assert elasticIp.address == elasticIpAddress
        return elasticIpResult
      }
      0 * _
    }
    def task = new AssociateElasticIpTask(flexService: flexService)

    when:
    def result = task.execute(new Stage(Execution.newPipeline("orca"), "associateElasticIp", [
      account  : account,
      region   : region,
      cluster  : cluster,
      elasticIp: [
        type   : elasticIpType,
        address: elasticIpAddress

      ]
    ]))

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context."notification.type" == task.getNotificationType()
    result.context."elastic.ip.assignment" == elasticIpResult

    where:
    account | region      | cluster      | elasticIpType | elasticIpAddress || application || elasticIpResult
    "test"  | "us-west-1" | "myapp-main" | "vpc"         | "10.0.0.1"       || "myapp"     || new ElasticIpResult()
    "test"  | "us-west-1" | "myapp-main" | "vpc"         | null             || "myapp"     || new ElasticIpResult()
  }
}
