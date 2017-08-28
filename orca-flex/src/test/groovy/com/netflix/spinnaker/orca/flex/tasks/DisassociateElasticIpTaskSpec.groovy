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
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification

class DisassociateElasticIpTaskSpec extends Specification {
  def "should delegate Elastic Ip disassociation to `flexService` and include result in outputs"() {
    given:
    def flexService = Mock(FlexService) {
      1 * disassociateElasticIp(application, account, cluster, region, elasticIpAddress) >> {
        return elasticIpResult
      }
      0 * _
    }
    def task = new DisassociateElasticIpTask(flexService: flexService)

    when:
    def result = task.execute(new Stage<>(new Pipeline("orca"), "associateElasticIp", [
      account  : account,
      region   : region,
      cluster  : cluster,
      elasticIp: [
        address: elasticIpAddress
      ]
    ]))

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context."notification.type" == task.getNotificationType()
    result.context."elastic.ip.assignment" == elasticIpResult

    where:
    account | region      | cluster      | elasticIpAddress || application || elasticIpResult
    "test"  | "us-west-1" | "myapp-main" | "10.0.0.1"       || "myapp"     || new ElasticIpResult()
  }
}
