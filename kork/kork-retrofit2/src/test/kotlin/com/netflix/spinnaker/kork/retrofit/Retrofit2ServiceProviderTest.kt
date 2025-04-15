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

package com.netflix.spinnaker.kork.retrofit

import brave.Tracing
import brave.http.HttpTracing
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.DefaultOkHttpClientBuilderProvider
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.config.okhttp3.RawOkHttpClientFactory
import com.netflix.spinnaker.config.DefaultServiceClientProvider
import com.netflix.spinnaker.kork.client.ServiceClientFactory
import com.netflix.spinnaker.kork.client.ServiceClientProvider
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor
import com.netflix.spinnaker.okhttp.SpinnakerRequestInterceptor
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import okhttp3.OkHttpClient
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Call
import retrofit2.http.Headers
import retrofit2.http.GET
import strikt.api.expect
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class Retrofit2ServiceProviderTest : JUnit5Minutests {

  fun tests() = rootContext {
    derivedContext<ApplicationContextRunner>("no configuration") {
      fixture {
        ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(
            Retrofit2ServiceFactoryAutoConfiguration::class.java,
            TestConfiguration::class.java
          ))
      }

      test("initializes service client provider") {
        run { ctx: AssertableApplicationContext ->
          val endpoint = DefaultServiceEndpoint("retrofit2", "https://www.test.com")
          expect {
            that(ctx.getBeansOfType(ServiceClientProvider::class.java))
              .get { size }
              .isEqualTo(1)

            that(ctx.getBean(ServiceClientProvider::class.java).getService(Retrofit2Service::class.java, endpoint))
              .isA<Retrofit2Service>()
          }
        }
      }

      test("initializes service client factories") {
        run { ctx: AssertableApplicationContext ->
          expect {
            that(ctx.getBeansOfType(ServiceClientFactory::class.java)).get { size }.isEqualTo(1)
          }
        }
      }

    }
  }

}

@Configuration
private open class TestConfiguration {

  @Bean
  open fun httpTracing(): HttpTracing =
    HttpTracing.create(Tracing.newBuilder().build())

  @Bean
  open fun okHttpClient(httpTracing: HttpTracing): OkHttpClient {
    return RawOkHttpClientFactory().create(OkHttpClientConfigurationProperties(), emptyList(), httpTracing)
  }

  @Bean
  open fun okHttpClientProvider(okHttpClient: OkHttpClient): OkHttpClientProvider {
    return OkHttpClientProvider(listOf(DefaultOkHttpClientBuilderProvider(okHttpClient,  OkHttpClientConfigurationProperties())), Retrofit2EncodeCorrectionInterceptor())
  }

  @Bean
  open fun spinnakerRequestInterceptor(): SpinnakerRequestInterceptor {
    return SpinnakerRequestInterceptor(true)
  }

  @Bean
  open fun objectMapper(): ObjectMapper {
    return  ObjectMapper()
  }

  @Bean
  open fun serviceClientProvider(
    serviceClientFactories: List<ServiceClientFactory?>, objectMapper: ObjectMapper): DefaultServiceClientProvider {
    return DefaultServiceClientProvider(serviceClientFactories, objectMapper)
  }

}
interface Retrofit2Service {

  @Headers("Accept: application/json")
  @GET("something/{paramId}")
  fun getSomething(@retrofit2.http.Path("paramId") paramId: String?): Call<*>?

}
