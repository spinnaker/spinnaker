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

package com.netflix.spinnaker.orca.notifications

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.discovery.StatusChangeEvent
import com.netflix.spinnaker.kork.eureka.EurekaStatusChangedEvent
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import net.greghaines.jesque.client.Client
import net.greghaines.jesque.worker.WorkerPool
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import rx.functions.Func1
import spock.lang.Specification
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.*
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

@ContextConfiguration(classes = [EmbeddedRedisConfiguration, DummyHandlerConfiguration, JesqueConfiguration])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class JesqueControlSpec extends Specification {

  @Autowired AbstractApplicationContext applicationContext
  @Autowired WorkerPool workerPool

  def "Jesque starts inactive"() {
    expect:
    workerPool.isPaused()
  }

  def "if the instance shuts down Jesque should stop"() {
    given:
    applicationContext.publishEvent(new EurekaStatusChangedEvent(new StatusChangeEvent(STARTING, UP)))

    when:
    applicationContext.publishEvent(new EurekaStatusChangedEvent(new StatusChangeEvent(UP, OUT_OF_SERVICE)))

    then:
    workerPool.isPaused()
  }

  def "if the instance starts up Jesque should start"() {
    when:
    applicationContext.publishEvent(new EurekaStatusChangedEvent(new StatusChangeEvent(STARTING, UP)))

    then:
    !workerPool.isPaused()
  }

  @Configuration
  static class DummyHandlerConfiguration {
    @Bean PropertyPlaceholderConfigurer propertyPlaceholderConfigurer() {
      new PropertyPlaceholderConfigurer()
    }

    @Bean
    AbstractPollingNotificationAgent agent(Client jesqueClient) {
      new AbstractPollingNotificationAgent(new ObjectMapper(), jesqueClient) {
        final long pollingInterval = 50L

        final String notificationType = "dummy"

        @Override
        protected Func1<Long, List<Map>> getEvents() {
          return null
        }

        @Override
        Class<? extends NotificationHandler> handlerType() {
          null
        }
      }
    }
  }
}
