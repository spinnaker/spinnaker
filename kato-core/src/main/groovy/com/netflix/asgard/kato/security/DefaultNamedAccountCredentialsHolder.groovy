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

package com.netflix.asgard.kato.security

import java.util.concurrent.ConcurrentHashMap

class DefaultNamedAccountCredentialsHolder implements NamedAccountCredentialsHolder {
  private static final Map<String, NamedAccountCredentials> accountCredentials = new ConcurrentHashMap<>()

  @Override
  void put(String name, NamedAccountCredentials namedAccountCredentials) {
    accountCredentials.put name, namedAccountCredentials
  }

  @Override
  NamedAccountCredentials getCredentials(String name) {
    accountCredentials.get name
  }

  @Override
  List<String> getAccountNames() {
    accountCredentials.keySet() as List<String>
  }
}
