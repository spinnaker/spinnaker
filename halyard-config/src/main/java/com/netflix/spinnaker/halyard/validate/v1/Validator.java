/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.validate.v1;

import java.util.stream.Stream;

/**
 * A validator that can pass, fail, or be skipped. It is stored inside the Validate* annotations.
 *
 * @see ValidateField
 *
 * Every instance implementing this class should be run at most once against a single Spinnaker deployment's configuration.
 */
public abstract class Validator<T> {
  protected Validator(T subject) {
    this.subject = subject;
  }

  /**
   * The value being validated.
   */
  protected final T subject;

  /**
   *  Describes what this validator is doing.
   *
   *  e.g. "Ensure that the Kubernetes account has a Docker Registry configured".
   */
  protected String description;

  /**
   * A list of human-readable error messages. If the stream is empty, it is assumed that the validator has passed.
   */
  abstract public Stream<String> validate();

  /**
   * When true, this validator won't be run.
   */
  abstract public boolean skip();
}
