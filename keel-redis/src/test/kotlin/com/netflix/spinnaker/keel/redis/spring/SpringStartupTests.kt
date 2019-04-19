package com.netflix.spinnaker.keel.redis.spring

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceVersionTracker
import com.netflix.spinnaker.keel.redis.RedisArtifactRepository
import com.netflix.spinnaker.keel.redis.RedisLock
import com.netflix.spinnaker.keel.redis.RedisResourceRepository
import com.netflix.spinnaker.keel.redis.RedisResourceVersionTracker
import com.netflix.spinnaker.keel.sync.Lock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.test.context.junit.jupiter.SpringExtension
import strikt.api.expectThat
import strikt.assertions.isA

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class],
  webEnvironment = MOCK,
  properties = [
    "redis.enabled=true",
    "redis.connection=redis://localhost:\${redisServer.port:6379}"
  ]
)
internal class SpringStartupTests {

  @Autowired
  lateinit var artifactRepository: ArtifactRepository

  @Autowired
  lateinit var resourceRepository: ResourceRepository

  @Autowired
  lateinit var resourceVersionTracker: ResourceVersionTracker

  @Autowired
  lateinit var lock: Lock

  @Test
  fun `uses RedisArtifactRepository`() {
    expectThat(artifactRepository).isA<RedisArtifactRepository>()
  }

  @Test
  fun `uses RedisResourceRepository`() {
    expectThat(resourceRepository).isA<RedisResourceRepository>()
  }

  @Test
  fun `uses RedisResourceVersionTracker`() {
    expectThat(resourceVersionTracker).isA<RedisResourceVersionTracker>()
  }

  @Test
  fun `uses RedisLock`() {
    expectThat(lock).isA<RedisLock>()
  }
}
