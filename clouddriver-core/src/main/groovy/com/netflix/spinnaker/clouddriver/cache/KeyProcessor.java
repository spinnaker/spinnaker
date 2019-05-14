/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.cache;

public interface KeyProcessor {

  /**
   * Indicates whether this processor can process the specified type
   *
   * @param type the cache type to process
   * @return <code>true</code> if this processor can process the specified type and <code>false
   *     </code> otherwise.
   */
  Boolean canProcess(String type);

  /**
   * Determines whether the underlying object represented by this key exists.
   *
   * @param key the cache key to process
   * @return <code>true</code> if the underlying object represented by this key exists and <code>
   *     false</code> otherwise.
   */
  Boolean exists(String key);
}
