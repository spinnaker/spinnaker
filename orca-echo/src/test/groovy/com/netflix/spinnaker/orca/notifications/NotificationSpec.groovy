package com.netflix.spinnaker.orca.notifications

import java.util.concurrent.CountDownLatch
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.echo.EchoEventPoller
import com.netflix.spinnaker.orca.echo.config.JesqueConfiguration
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.notifications.jenkins.BuildJobNotificationHandler
import com.netflix.spinnaker.orca.notifications.jenkins.BuildJobPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.jenkins.Trigger
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import net.greghaines.jesque.Config
import net.greghaines.jesque.ConfigBuilder
import net.greghaines.jesque.client.Client
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Scope
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.context.ContextConfiguration
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.util.Pool
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import rx.schedulers.Schedulers
import spock.lang.Specification
import static java.util.concurrent.TimeUnit.SECONDS
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

/**
 * Integration test to ensure the Jesque-based notification workflow hangs
 * together.
 */
@ContextConfiguration(classes = [JedisConfiguration, TestConfiguration, JesqueConfiguration])
class NotificationSpec extends Specification {

  @Autowired AbstractApplicationContext applicationContext
  @Autowired BuildJobPollingNotificationAgent notificationAgent
  def pipelineStarter = Mock(PipelineStarter)
  def scheduler = Schedulers.test()

  def setupSpec() {
    System.setProperty("echo.baseUrl", "http://echo")
    System.setProperty("mayo.baseUrl", "http://mayo")
  }

  def cleanupSpec() {
    System.clearProperty("echo.baseUrl")
    System.clearProperty("mayo.baseUrl")
  }

  def setup() {
    notificationAgent.shutdown()
    notificationAgent.scheduler = scheduler

    applicationContext.beanFactory.with {
      registerSingleton "pipelineStarter", pipelineStarter
    }
  }

  def "jenkins trigger causes a pipeline to start"() {
    given:
    def latch = new CountDownLatch(1)
    pipelineStarter.start(*_) >> { latch.countDown() }

    and:
    notificationAgent.init()

    when:
    tick()

    then:
    latch.await(1, SECONDS)
  }

  private tick() {
    scheduler.advanceTimeBy(notificationAgent.pollingInterval, SECONDS)
  }
}

@Import(PropertyPlaceholderAutoConfiguration)
@Configuration
class TestConfiguration {

  @Bean ObjectMapper objectMapper() {
    new OrcaObjectMapper()
  }

  @Bean PipelineIndexer buildJobPipelineIndexer() {
    [getPipelines: { ->
      def trigger = new Trigger("master1", "SPINNAKER-package-pond")
      def pipeline = [
        name    : "pipeline1",
        triggers: [[type  : "jenkins",
                    job   : "SPINNAKER-package-pond",
                    master: "master1"]],
        stages  : [[type: "bake"],
                   [type: "deploy", cluster: [name: "bar"]]]
      ]
      ImmutableMap.of(trigger, [pipeline])
    }] as PipelineIndexer
  }

  @Bean EchoEventPoller echoEventPoller(ObjectMapper mapper) {
    def content = [[
                     content: [
                       project: [
                         name     : "SPINNAKER-package-pond",
                         lastBuild: [result: "SUCCESS", building: false]
                       ],
                       master : "master1"
                     ]
                   ]]
    def response = new Response("http://echo", 200, "OK", [],
      new TypedByteArray("application/json", mapper.writeValueAsString(content).bytes))
    [getEvents: { String type -> response }] as EchoEventPoller
  }

  @Bean
  BuildJobPollingNotificationAgent buildJobPollingNotificationAgent(
    ObjectMapper mapper,
    EchoEventPoller echoEventPoller,
    Client jesqueClient) {
    new BuildJobPollingNotificationAgent(mapper, echoEventPoller, jesqueClient)
  }

  @Bean @Scope(SCOPE_PROTOTYPE)
  BuildJobNotificationHandler buildJobNotificationHandler(Map input) {
    new BuildJobNotificationHandler(input)
  }
}

/**
 * This configuration class sets up embedded Redis for Jesque if a Redis URL is
 * not specified in system properties.
 */
@Configuration
class JedisConfiguration {
  @Bean
  @ConditionalOnExpression("#{systemProperties['redis.connection'] == null}")
  EmbeddedRedis redisServer() {
    EmbeddedRedis.embed()
  }

  @Bean
  @ConditionalOnBean(EmbeddedRedis)
  Config jesqueConfig(EmbeddedRedis redis) {
    new ConfigBuilder()
      .withHost("localhost")
      .withPort(redis.redisServer.port)
      .build()
  }

  @Bean
  @ConditionalOnBean(EmbeddedRedis)
  Pool<Jedis> jedisPool(EmbeddedRedis redis) {
    new JedisPool("localhost", redis.redisServer.port)
  }
}

