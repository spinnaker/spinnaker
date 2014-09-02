/*
 * Copyright 2014 Netflix, Inc.
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



package com.netflix.spinnaker.front50.config

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.front50.model.application.ApplicationDAOProvider
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Created by aglover on 5/9/14.
 */
@Component
public class HealthCheck implements HealthIndicator {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  List<ApplicationDAOProvider> applicationDAOProviders

  @Override
  public Health health() {
    try {
      for (account in accountCredentialsProvider.all) {
        def providers = applicationDAOProviders.findAll { it.supports(account.getClass()) }

        // I may have an account, but nobody to supply me an ApplicationDAO instance... This seems weird, but let's just skip.
        for (provider in providers) {
          if (!provider.getForAccount(account).healthly) {
            throw new RuntimeException()
          }
        }
      }
      new Health.Builder().up() build()
    } catch (IGNORE) {
      throw new NotHealthlyException()
    }
  }

  @InheritConstructors
  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Not Healthy!")
  public class NotHealthlyException extends RuntimeException {}
}
