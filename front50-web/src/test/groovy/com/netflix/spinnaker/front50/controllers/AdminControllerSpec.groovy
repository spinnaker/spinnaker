/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.front50.controllers

import com.netflix.spinnaker.front50.model.AdminOperations
import com.netflix.spinnaker.front50.model.ObjectType
import spock.lang.Specification
import spock.lang.Subject

class AdminControllerSpec extends Specification {

  AdminOperations adminOperations = Mock(AdminOperations)

  @Subject
  AdminController controller = new AdminController([adminOperations])


  def 'should recover application and permission record'() {
    given:
    AdminOperations.Recover operation = new AdminOperations.Recover('application', 'test-app')

    when:
    controller.recover(operation)

    then:
    noExceptionThrown()
    2 * adminOperations.recover(_) >> { AdminOperations.Recover op ->
      assert op.objectId == 'test-app'
      assert op.objectType.toLowerCase() in [ObjectType.APPLICATION.clazz.simpleName.toLowerCase(), ObjectType.APPLICATION_PERMISSION.clazz.simpleName.toLowerCase()]
    }
    0 * _
  }

}
