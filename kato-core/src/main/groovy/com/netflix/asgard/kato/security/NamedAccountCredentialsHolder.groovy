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

/**
 * An implementation of this interface will provide a correlation of a string name to a {@link NamedAccountCredentials}
 * object, which can be used to credentials by an arbitrary name.
 *
 * @author Dan Woods
 */
public interface NamedAccountCredentialsHolder {
  /**
   * Retrieves a {@link NamedAccountCredentials} object by the supplied name.
   *
   * @param name
   * @return a NamedAccountCredentials object.
   */
  NamedAccountCredentials getCredentials(String name)

  /**
   * Provides callers with a list of the available names in the holder's internal repository.
   *
   * @return list of account names
   */
  List<String> getAccountNames()

  /**
   * Stores a {@link NamedAccountCredentials} object in the holder's internal repository, keyed on the supplied name.
   *
   * @param name
   * @param namedAccountCredentials
   */
  void put(String name, NamedAccountCredentials namedAccountCredentials)
}