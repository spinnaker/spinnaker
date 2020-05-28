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


package com.netflix.spinnaker.front50

import com.netflix.spinnaker.front50.ItemDAOHealthIndicator
import com.netflix.spinnaker.front50.model.application.ApplicationDAO
import org.springframework.boot.actuate.health.Status
import org.springframework.scheduling.TaskScheduler
import spock.lang.Specification
import spock.lang.Subject

class ItemDAOHealthIndicatorSpec extends Specification {
  ApplicationDAO dao = Mock(ApplicationDAO)

  @Subject
  ItemDAOHealthIndicator healthCheck = new ItemDAOHealthIndicator(dao, Stub(TaskScheduler))

  void 'health check should return 5xx error if dao is not working'() {
    when:
    healthCheck.run()
    def result = healthCheck.health()

    then:
    1 * dao.isHealthy() >> false
    result.status == Status.DOWN
  }

  void 'health check should return 5xx error if dao throws an error'() {
    when:
    healthCheck.run()
    def result = healthCheck.health()

    then:
    1 * dao.isHealthy() >> { throw new RuntimeException("Boom goes the dynamite") }
    result.status == Status.DOWN
  }

  void 'health check should return Ok'() {
    when:
    healthCheck.run()
    def result = healthCheck.health()

    then:
    1 * dao.isHealthy() >> true
    result.status == Status.UP
  }
}
