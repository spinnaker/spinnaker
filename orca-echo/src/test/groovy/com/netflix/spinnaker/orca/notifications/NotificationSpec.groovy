package com.netflix.spinnaker.orca.notifications

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.echo.config.EchoConfiguration
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.mayo.MayoService
import com.netflix.spinnaker.orca.mayo.services.PipelineConfigurationService
import com.netflix.spinnaker.orca.notifications.manual.ManualTriggerPollingNotificationAgent
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import net.greghaines.jesque.ConfigBuilder
import net.greghaines.jesque.client.Client
import net.greghaines.jesque.client.ClientImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

/**
 * Integration test to ensure the Jesque-based notification workflow hangs
 * together.
 */
@ContextConfiguration(classes = [JedisConfiguration, TestConfiguration, EchoConfiguration])
class NotificationSpec extends Specification {

  def pipelineStarter = Mock(PipelineStarter)
  @Autowired AbstractApplicationContext applicationContext
  @Autowired ManualTriggerPollingNotificationAgent notificationAgent

  def setup() {
    applicationContext.beanFactory.with {
      registerSingleton "pipelineStarter", pipelineStarter
    }
  }

  def "manual trigger causes a pipeline to start"() {
    when:
    notificationAgent.handleNotification([[content: [pipeline: "foo"]]])

    then:
    1 * pipelineStarter.start(*_)
  }
}

@Configuration
class TestConfiguration {
  @Bean PropertyPlaceholderConfigurer propertyPlaceholderConfigurer() {
    new PropertyPlaceholderConfigurer()
  }

  @Bean ObjectMapper objectMapper() {
    new OrcaObjectMapper()
  }

  @Bean MayoService mayoService() {
    [:] as MayoService
  }

  @Bean PipelineConfigurationService pipelineConfigurationService() {
    new PipelineConfigurationService(pipelines: [
        [:]
    ])
  }

  @Bean ExecutionRepository executionRepository() {
    [:] as ExecutionRepository
  }
}

@Configuration
class JedisConfiguration {
  @Bean
//    @ConditionalOnExpression('\'${redis.connection}\' == null')
  EmbeddedRedis redisServer() {
    EmbeddedRedis.embed()
  }

  @Bean
  Client jesqueClient(EmbeddedRedis redis) {
    def jesqueConfig = new ConfigBuilder()
        .withHost("localhost")
        .withPort(redis.redisServer.port)
        .build()
    new ClientImpl(jesqueConfig)
  }
}

