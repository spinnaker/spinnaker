package com.netflix.spinnaker.keel.redis.spring

import com.netflix.spinnaker.keel.RuleEngineApp
import com.netflix.spinnaker.keel.redis.RedisPluginRepository
import com.netflix.spinnaker.keel.registry.PluginRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import org.springframework.test.context.junit4.SpringRunner
import strikt.api.expect
import strikt.assertions.isA

@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [RuleEngineApp::class],
  webEnvironment = NONE,
  properties = ["redis.connection=redis://localhost:\${redisServer.port:6379}"]
)
internal class SpringStartupTests {

  @Autowired
  lateinit var pluginRepository: PluginRepository

  @Test
  fun `uses RedisPluginRepository`() {
    expect(pluginRepository).isA<RedisPluginRepository>()
  }
}
