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

package com.netflix.spinnaker.clouddriver.aws.deploy.description

import com.amazonaws.services.cloudwatch.model.ComparisonOperator
import com.amazonaws.services.cloudwatch.model.Dimension
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest
import com.amazonaws.services.cloudwatch.model.StandardUnit
import com.amazonaws.services.cloudwatch.model.Statistic
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupNameable

class UpsertAlarmDescription extends AbstractAmazonCredentialsDescription implements ServerGroupNameable {
  String name
  String asgName
  String region

  Boolean actionsEnabled = true

  String alarmDescription
  ComparisonOperator comparisonOperator

  Collection<Dimension> dimensions

  Integer evaluationPeriods
  Integer period = 300
  Double threshold

  String namespace
  String metricName

  Statistic statistic

  StandardUnit unit

  Collection<String> alarmActionArns
  Collection<String> insufficientDataActionArns
  Collection<String> okActionArns

  PutMetricAlarmRequest buildRequest() {
    new PutMetricAlarmRequest(
        alarmName: name,
        actionsEnabled: actionsEnabled,
        alarmDescription: alarmDescription,
        comparisonOperator: comparisonOperator,
        evaluationPeriods: evaluationPeriods,
        period: period,
        threshold: threshold,
        namespace: namespace,
        metricName: metricName,
        statistic: statistic,
        unit: unit,
        dimensions: dimensions,
        alarmActions: alarmActionArns,
        insufficientDataActions: insufficientDataActionArns,
        oKActions: okActionArns
    )
  }

  @Override
  Collection<String> getServerGroupNames() {
    return [asgName]
  }
}
