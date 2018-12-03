package com.netflix.spinnaker.keel.redis.spring

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.ResourceVersionTracker
import com.netflix.spinnaker.keel.redis.RedisAssetRepository
import com.netflix.spinnaker.keel.redis.RedisResourceVersionTracker
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import org.springframework.test.context.junit4.SpringRunner
import strikt.api.expectThat
import strikt.assertions.isA

@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [KeelApplication::class],
  webEnvironment = NONE,
  properties = ["redis.connection=redis://localhost:\${redisServer.port:6379}"]
)
internal class SpringStartupTests {

  @Autowired
  lateinit var assetRepository: AssetRepository

  @Autowired
  lateinit var resourceVersionTracker: ResourceVersionTracker

  @Test
  fun `uses RedisAssetRepository`() {
    expectThat(assetRepository).isA<RedisAssetRepository>()
  }

  @Test
  fun `uses RedisResourceVersionTracker`() {
    expectThat(resourceVersionTracker).isA<RedisResourceVersionTracker>()
  }
}
