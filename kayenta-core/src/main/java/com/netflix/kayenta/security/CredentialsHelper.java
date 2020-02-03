/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public class CredentialsHelper {

  public static String resolveAccountByNameOrType(
      String accountName,
      AccountCredentials.Type accountType,
      AccountCredentialsRepository accountCredentialsRepository) {
    AccountCredentials credentials;

    if (StringUtils.hasLength(accountName)) {
      credentials = accountCredentialsRepository.getRequiredOne(accountName);
    } else {
      credentials =
          accountCredentialsRepository
              .getOne(accountType)
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Unable to resolve account of type " + accountType + "."));
    }

    return credentials.getName();
  }

  public static Set<AccountCredentials> getAllAccountsOfType(
      AccountCredentials.Type accountType,
      AccountCredentialsRepository accountCredentialsRepository) {
    return accountCredentialsRepository.getAll().stream()
        .filter(credentials -> credentials.getSupportedTypes().contains(accountType))
        .collect(Collectors.toSet());
  }
}
