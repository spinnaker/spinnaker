/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.gate.services

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.util.concurrent.UncheckedExecutionException
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import com.netflix.spinnaker.gate.services.internal.ClouddriverService.AccountDetails
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicReference

/**
 * DefaultProviderLookupService.
 */
@Slf4j
@Component("providerLookupService")
class DefaultProviderLookupService implements ProviderLookupService, AccountLookupService {

  private static final String FALLBACK = "unknown"
  private static final TypeReference<List<Map>> JSON_LIST = new TypeReference<List<Map>>() {}
  private static final TypeReference<List<AccountDetails>> ACCOUNT_DETAILS_LIST = new TypeReference<List<AccountDetails>>() {}

  private final ClouddriverService clouddriverService
  private final ObjectMapper mapper = new ObjectMapper()

  private final AtomicReference<List<AccountDetails>> accountsCache = new AtomicReference<>([])

  @Autowired
  DefaultProviderLookupService(ClouddriverService clouddriverService) {
    this.clouddriverService = clouddriverService
  }

  @Scheduled(fixedDelay = 30000L)
  void refreshCache() {
    try {
      accountsCache.set(clouddriverService.getAccountDetails())
    } catch (Exception e) {
      log.error("Unable to refresh account details cache, reason: ${e.message}")
    }
  }

  @Override
  String providerForAccount(String account) {
    try {
      return accountsCache.get()?.find { it.name == account }?.type ?: FALLBACK
    } catch (ExecutionException | UncheckedExecutionException ex) {
      return FALLBACK
    }
  }

  @Override
  List<AccountDetails> getAccounts() {
    final List<AccountDetails> original = accountsCache.get()
    final List<Map> accountsCopy = mapper.convertValue(original, JSON_LIST)
    return mapper.convertValue(accountsCopy, ACCOUNT_DETAILS_LIST)
  }
}
