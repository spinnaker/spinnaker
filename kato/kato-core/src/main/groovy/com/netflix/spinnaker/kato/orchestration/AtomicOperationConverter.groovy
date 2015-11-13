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

package com.netflix.spinnaker.kato.orchestration
/**
 * Implementations of this interface will provide an object capable of converting a Map of input parameters to an
 * operation's description object and an {@link com.netflix.spinnaker.kato.orchestration.AtomicOperation} instance.
 *
 *
 */
interface AtomicOperationConverter {
  /**
   * This method takes a Map input and converts it to an {@link com.netflix.spinnaker.kato.orchestration.AtomicOperation} instance.
   *
   * @param input
   * @return atomic operation
   */
  AtomicOperation convertOperation(Map input)

  /**
   * This method takes a Map input and creates a description object, that will often be used by an {@link AtomicOperation}.
   *
   * @param input
   * @return instance of an operation description object
   */
  Object convertDescription(Map input)
}
