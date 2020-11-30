package com.netflix.spinnaker.keel.integration

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.spring.test.DisableSpringScheduling
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.context.annotation.Bean
import retrofit2.Retrofit
import retrofit2.http.GET
import strikt.api.Assertion
import strikt.api.expectThat
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

// TODO: this doesn't really need to be an integration test except that it's painful to configure
//       the retrofit client without Spring
@SpringBootTest(
  classes = [KeelApplication::class, TestConfiguration::class],
  webEnvironment = MOCK
)
@DisableSpringScheduling
internal class SchedulingResilienceTests
@Autowired constructor(val service: DummyRetrofitService){

  @Test
  fun `retrofit call completes even if an interceptor throws an exception`() {
    val latch = CountDownLatch(1)
    GlobalScope.launch {
      try {
        service.greet()
      } finally {
        latch.countDown()
      }
    }
    expectThat(latch).countsDownWithin(1)
  }
}

fun Assertion.Builder<CountDownLatch>.countsDownWithin(timeoutSeconds: Long): Assertion.Builder<CountDownLatch> {
  require(timeoutSeconds > 0)
  return assert("Counts down within $timeoutSeconds seconds") {
    if (it.await(timeoutSeconds, SECONDS)) {
      pass()
    } else {
      fail("Still awaiting ${it.count} tick${if (it.count > 1) "s" else ""}")
    }
  }
}

internal interface DummyRetrofitService {
  @GET("/")
  suspend fun greet(): ResponseBody
}

private class TestConfiguration {
  @Bean
  fun server() = MockWebServer()

  @Bean
  fun bedShittingInterceptor() = Interceptor { throw IOException("💩🛏") }

  @Bean
  fun dummyRetrofitService(
    retrofitClient: OkHttpClient,
    server: MockWebServer
  ): DummyRetrofitService =
    Retrofit
      .Builder()
      .baseUrl(server.url("/"))
      .client(retrofitClient)
      .build()
      .create(DummyRetrofitService::class.java)
}
