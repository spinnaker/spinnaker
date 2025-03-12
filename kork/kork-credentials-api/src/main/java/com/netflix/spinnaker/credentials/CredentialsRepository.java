/*
 * Copyright 2020 Armory
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
 */

package com.netflix.spinnaker.credentials;

import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Repository of credentials of a given type
 *
 * @param <T>
 */
public interface CredentialsRepository<T extends Credentials> extends SpinnakerExtensionPoint {
  /**
   * @param name
   * @return Credentials with the given name or null
   */
  @Nullable
  T getOne(String name);

  /**
   * @param name
   * @return true if the repository holds credentials of the given name
   */
  boolean has(String name);

  /** @return A new set containing all known credentials */
  Set<T> getAll();

  /**
   * Add or update credentials
   *
   * @param credentials
   * @return credentials
   * @throws com.netflix.spinnaker.kork.exceptions.InvalidCredentialsTypeException
   */
  void save(T credentials);

  /**
   * Remove credentials with the given name
   *
   * @param name
   */
  void delete(String name);

  /** @return Type of credentials this repository can store */
  String getType();
}
