/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.exceptions;

import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;

public class TemplateRenderException extends RuntimeException {

  public static TemplateRenderException fromError(Error error) {
    return new TemplateRenderException(error.getMessage(), null, error);
  }

  public static TemplateRenderException fromError(Error error, Throwable cause) {
    TemplateRenderException e = new TemplateRenderException(error.getMessage(), cause, error);

    if (cause instanceof TemplateRenderException) {
      error.withNested(((TemplateRenderException) cause).getErrors());
    } else if (error.getCause() == null) {
      error.withCause(cause.getMessage());
    }

    return e;
  }

  private Errors errors = new Errors();

  public TemplateRenderException(String message, Throwable cause, Errors errors) {
    this(message, cause);
    this.errors = errors;
  }

  private TemplateRenderException(String message, Throwable cause, Error error) {
    this(message, cause);
    this.errors.add(error);
  }

  public TemplateRenderException(Error error) {
    this(error.getMessage());
    this.errors.add(error);
  }

  public TemplateRenderException(String message) {
    super(message);
  }

  public TemplateRenderException(String message, Throwable cause) {
    super(message, cause);
  }

  public Errors getErrors() {
    return errors;
  }
}
