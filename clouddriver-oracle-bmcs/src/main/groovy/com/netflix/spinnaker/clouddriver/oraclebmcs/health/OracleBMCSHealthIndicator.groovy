/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.health

import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.oracle.bmc.identity.requests.ListAvailabilityDomainsRequest
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus

import java.util.concurrent.atomic.AtomicReference

@Slf4j
@Component
class OracleBMCSHealthIndicator implements HealthIndicator {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  private final AtomicReference<Exception> lastException = new AtomicReference<>(null)

  @Override
  Health health() {
    def ex = lastException.get()

    if (ex) {
      throw ex
    }

    new Health.Builder().up().build()
  }

  @Scheduled(fixedDelay = 300000L)
  void checkHealth() {
    try {
      Set<OracleBMCSNamedAccountCredentials> creds = accountCredentialsProvider.all.findAll {
        it instanceof OracleBMCSNamedAccountCredentials
      } as Set<OracleBMCSNamedAccountCredentials>

      creds.each { OracleBMCSNamedAccountCredentials cred ->
        try {
          cred.identityClient.listAvailabilityDomains(ListAvailabilityDomainsRequest.builder().compartmentId(cred.compartmentId).build())
        } catch(Exception ex) {
          throw new OracleBMCSUnreachableException(ex)
        }
      }

      lastException.set(null)
    } catch (Exception ex) {
      log.warn "Unhealthy", ex
      lastException.set(ex)
    }
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = 'Could not reach the Oracle BMCS service.')
  @InheritConstructors
  static class OracleBMCSUnreachableException extends RuntimeException {}
}

