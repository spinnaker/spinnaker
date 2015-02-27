package com.netflix.spinnaker.orca.notifications
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.echo.EchoEventPoller
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.notifications.jenkins.BuildJobNotificationHandler
import com.netflix.spinnaker.orca.notifications.jenkins.BuildJobPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.jenkins.Trigger
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import net.greghaines.jesque.client.Client
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Scope
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.context.ContextConfiguration
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import rx.schedulers.Schedulers
import spock.lang.Specification

import java.util.concurrent.CountDownLatch

import static java.util.concurrent.TimeUnit.SECONDS
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE
/**
 * Integration test to ensure the Jesque-based notification workflow hangs
 * together.
 */
@ContextConfiguration(classes = [TestConfiguration, EmbeddedRedisConfiguration, JesqueConfiguration])
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
