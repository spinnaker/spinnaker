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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.*
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteAlarmDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Subject

class DeleteAlarmOperationUnitSpec extends Specification {
  private static final String ACCOUNT = "test"

  def credz = Stub(NetflixAmazonCredentials) {
    getName() >> ACCOUNT
  }

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def description = new DeleteAlarmDescription(
    region: "us-west-1",
    names: ["alarm1", "alarm2"],
    credentials: credz
  )

  def cloudWatch = Mock(AmazonCloudWatch)
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getCloudWatch(credz, "us-west-1", true) >> cloudWatch
  }

  @Subject def op = new DeleteAlarmAtomicOperation(description)

  def setup() {
    op.amazonClientProvider = amazonClientProvider
  }

  void "deletes alarms"() {

    when:
    op.operate([])

    then:
    1 * cloudWatch.deleteAlarms(new DeleteAlarmsRequest(
      alarmNames: ["alarm1", "alarm2"]
    ))
  }

}
