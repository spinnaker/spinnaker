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

/** Credentials to an external system. Each credentials has a unique name for its type. */
public interface Credentials {

  /**
   * Each credentials is uniquely identified by its name for a given type.
   *
   * @return Credentials name
   */
  String getName();

  /**
   * @return Credentials type
   */
  String getType();
}
