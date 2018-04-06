/*
 * Copyright 2018 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import org.openstack4j.openstack.heat.domain.HeatStack
import spock.lang.Specification

class StackCheckerTest extends Specification {

  def "should return true when heat stack status is CREATE_COMPLETE"() {
    given:
    def checker = new StackChecker(StackChecker.Operation.CREATE)
    when:
    def ready = checker.isReady(new HeatStack(status: "CREATE_COMPLETE"))
    then:
    ready
  }

  def "should return false when heat stack status is CREATE_IN_PROGRESS"() {
    given:
    def checker = new StackChecker(StackChecker.Operation.CREATE)
    when:
    def ready = checker.isReady(new HeatStack(status: "CREATE_IN_PROGRESS"))
    then:
    !ready
  }

  def "should thrown an exception when heat stack status is CREATE_FAILED"() {
    given:
    def checker = new StackChecker(StackChecker.Operation.CREATE)
    when:
    checker.isReady(new HeatStack(status: "CREATE_FAILED"))
    then:
    thrown(OpenstackProviderException)
  }

  def "should thrown an exception when heat stack status is unknown"() {
    given:
    def checker = new StackChecker(StackChecker.Operation.CREATE)
    when:
    checker.isReady(new HeatStack(status: "UNKNOWN_STATUS"))
    then:
    thrown(OpenstackProviderException)
  }

  def "should thrown an exception when stack is null"() {
    given:
    def checker = new StackChecker(StackChecker.Operation.CREATE)
    when:
    checker.isReady(null)
    then:
    thrown(OpenstackProviderException)
  }

  def "should return true when stack is null but operation is delete"() {
    given:
    def checker = new StackChecker(StackChecker.Operation.DELETE)
    when:
    def ready = checker.isReady(null)
    then:
    ready
    noExceptionThrown()
  }

}
