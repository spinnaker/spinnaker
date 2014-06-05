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

import groovy.transform.CompileStatic

import java.util.concurrent.ConcurrentHashMap

/**
 * A default implementation of the {@link NamedAccountProvider} interface, which provides an in-memory storage for named accounts.
 *
 * @author Dan Woods
 */
@CompileStatic
class DefaultNamedAccountProvider implements NamedAccountProvider {
  private static final Map<String, NamedAccount> storage = new ConcurrentHashMap()

  @Override
  List<String> getAccountNames() {
    storage.keySet() as List
  }

  @Override
  NamedAccount get(String name) {
    storage.get name
  }

  @Override
  void remove(String name) {
    storage.remove name
  }

  @Override
  void put(NamedAccount namedAccount) {
    storage.put namedAccount.name, namedAccount
  }
}
