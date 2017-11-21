/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.keel.clouddriver.okhttp

import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Credential
import com.squareup.okhttp.Interceptor
import com.squareup.okhttp.Response
import org.slf4j.LoggerFactory
import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.annotation.Scheduled
import retrofit.RetrofitError

/**
 * Builds a local cache of clouddriver credentials that is used to populate X-SPINNAKER-ACCOUNTS header, allowing
 * Keel to perform orchestrations against any account. Security of applying an Intent should be performed at Intent
 * submission time into Keel, validating the that user who is submitting the Intent has access to affect the accounts
 * and applications the Intent touches.
 *
 * TODO rz - should have an account provider that is injected, rather than each interceptor running its own cache
 */
class AccountProvidingNetworkInterceptor(
  private val applicationContext: ApplicationContext
) : Interceptor {

  private val log = LoggerFactory.getLogger(javaClass)

  private var credentials: Set<Credential> = setOf()

  override fun intercept(chain: Interceptor.Chain): Response
    = chain.proceed(chain.request().newBuilder()
        .header("X-SPINNAKER-ACCOUNTS", credentials.joinToString(",") { it.name })
        .build()
    )

  @Scheduled(initialDelay = 0L, fixedDelay = 60000L) fun refreshCredentials() {
    log.info("Refreshing Clouddriver credentials")
    val cloudDriver: CloudDriverService
    try {
      cloudDriver = applicationContext.getBean(CloudDriverService::class.java)
    } catch (e: BeansException) {
      // Not really a bad thing, but could potentially happen during startup.
      log.warn("Attempted to retrieve credentials from CloudDriverService, but Bean is not yet configured", e)
      return
    }

    try {
      credentials = cloudDriver.listCredentials()
    } catch (e: RetrofitError) {
      log.error("Failed to refresh credentials from CloudDriverService", e)
    }

    log.info("Authorized credentials: ${credentials.joinToString(",") { it.name }}")
  }
}
