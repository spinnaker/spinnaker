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

package com.netflix.spinnaker.halyard.config.validate.v1;

import com.netflix.spinnaker.halyard.config.model.v1.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.Reference;

import java.lang.reflect.InvocationTargetException;
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
    this.context = null;
    this.subject = subject;
  }

  protected Validator(Halconfig context, T subject) {
    this.context = context;
    this.subject = subject;
  }

  /**
   * The value being validated.
   */
  protected final T subject;

  /**
   * An optional halconfig to user when performing validation. If a validator needs a context to perform validation, it
   * should implement the constructor that takes `context` as an argument.
   */
  protected final Halconfig context;

  /**
   *  Describes what this validator is doing.
   *
   *  e.g. "Ensure that the Kubernetes account has a Docker Registry configured".
   */
  protected String description;

  /**
   * A list of human-readable error messages.
   *
   * @return a stream of all validation errors encountered. Empty i.f.f this validator passed. Should never be null.
   */
  abstract public Stream<String> validate();

  /**
   * Indicates whether or not this validator should be run.
   *
   * @return true i.f.f. this validator should be skipped.
   */

  abstract public boolean skip();

  public static Validator construct(Halconfig context, Class<? extends Validator> v, Reference<?> reference)
      throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    // Attempt to create an instance of the validator providing the context. If such a constructor isn't declared,
    // we retry using the non-context constructor.
    try {
      return v.getDeclaredConstructor(Halconfig.class, reference.getValueType()).newInstance(context, reference.getValue());
    } catch (IllegalArgumentException e) {
      return construct(v, reference);
    } catch (NoSuchMethodException e) {
      return construct(v, reference);
    }
  }

  private static Validator construct(Class<? extends Validator> v, Reference<?> reference)
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return v.getDeclaredConstructor(reference.getValueType()).newInstance(reference.getValue());
  }
}
