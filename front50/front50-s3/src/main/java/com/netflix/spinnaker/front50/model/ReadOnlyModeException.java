/*
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.spinnaker.front50.model;

/** Thrown when front50 is running in read-only mode and a write operation is triggered. */
public class ReadOnlyModeException extends IllegalStateException {

  private static final String DEFAULT_MESSAGE = "Cannot perform write operation, in read-only mode";

  public ReadOnlyModeException() {
    this(DEFAULT_MESSAGE);
  }

  public ReadOnlyModeException(String message) {
    super(message);
  }
}
