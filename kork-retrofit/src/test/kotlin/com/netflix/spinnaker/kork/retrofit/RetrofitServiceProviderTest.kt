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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.RetrofitConfiguration
import com.netflix.spinnaker.config.okhttp3.DefaultOkHttpClientBuilderProvider
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.config.okhttp3.RawOkHttpClientFactory
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import com.netflix.spinnaker.okhttp.SpinnakerRequestInterceptor
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import okhttp3.OkHttpClient
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import strikt.api.expect
import strikt.assertions.isEqualTo

class RetrofitServiceProviderTest  : JUnit5Minutests {

  fun tests() = rootContext {
    derivedContext<ApplicationContextRunner>("no configuration") {
      fixture {
        ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(
            RetrofitServiceFactoryAutoConfiguration::class.java,
            RetrofitConfiguration::class.java,
            TestConfiguration::class.java
          ))
      }

      test("initializes service client provider") {
        run { ctx: AssertableApplicationContext ->
          expect {
            that(ctx.getBeansOfType(RetrofitServiceProvider::class.java)).get { size }.isEqualTo(1)
          }
        }
      }
    }
  }

}

@Configuration
private open class TestConfiguration {

  @Bean
  open fun okHttpClient(): OkHttpClient {
    return RawOkHttpClientFactory().create(OkHttpClientConfigurationProperties(), emptyList())
  }

  @Bean
  open fun okHttpClientProvider(okHttpClient: OkHttpClient): OkHttpClientProvider {
    return OkHttpClientProvider(listOf(DefaultOkHttpClientBuilderProvider(okHttpClient,  OkHttpClientConfigurationProperties())))
  }

  @Bean
  open fun spinnakerRequestInterceptor(): SpinnakerRequestInterceptor {
    return SpinnakerRequestInterceptor(OkHttpClientConfigurationProperties())
  }

  @Bean
  open fun objectMapper(): ObjectMapper {
    return  ObjectMapper()
  }

}
