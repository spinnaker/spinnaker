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

package com.netflix.spinnaker.oort.security

import java.util.concurrent.ConcurrentHashMap

class DefaultNamedAccountCredentialsProvider implements NamedAccountCredentialsProvider {
  private static final Map<String, NamedAccountCredentials> registry = new ConcurrentHashMap<>()

  @Override
  NamedAccountCredentials getAccount(String name) {
    if (registry.containsKey(name)) {
      registry[name]
    } else {
      null
    }
  }

  @Override
  void putAccount(NamedAccountCredentials account) {
    if (account.name) {
      registry.put(account.name, account)
    } else {
      throw new IllegalArgumentException("Account name cannot be null")
    }
  }

  @Override
  void remove(NamedAccountCredentials account) {
    if (registry.containsValue(account)) {
      registry.remove(account)
    }
  }

  @Override
  List<NamedAccountCredentials> list() {
    registry.values() as List
  }
}
