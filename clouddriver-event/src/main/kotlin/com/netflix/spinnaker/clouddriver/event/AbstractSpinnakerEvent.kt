/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.event

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.event.exceptions.UninitializedEventException

/**
 * WARNING: Do not use this base class with Lombok events, you will have a bad time! Only use in Kotlin classes.
 * For some reason, Lombok / Jackson can't find methods to deserialize, so the Java classes have to implement the
 * interface directly. I'm not sure if this is a result of writing in Kotlin, or an issue in Lombok and/or Jackson.
 */
abstract class AbstractSpinnakerEvent : SpinnakerEvent {
  /**
   * Not a lateinit to make Java/Lombok & Jackson compatibility a little easier, although behavior is exactly the same.
   */
  private var metadata: EventMetadata? = null

  @JsonIgnore
  override fun getMetadata(): EventMetadata {
    return metadata ?: throw UninitializedEventException()
  }

  override fun setMetadata(eventMetadata: EventMetadata) {
    metadata = eventMetadata
  }

  fun hasMetadata() = metadata != null
}
