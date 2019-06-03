/*
 * Copyright 2018 Google, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.core.resource.v1;

import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.FatalTemplateErrorsException;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;

public abstract class JinjaTemplatedResource extends TemplatedResource {
  private Jinjava jinjava = new Jinjava();

  @Override
  public String toString() {
    String contents = getContents();
    try {
      return jinjava.render(contents, bindings);
    } catch (FatalTemplateErrorsException e) {
      throw new HalException(
          Problem.Severity.FATAL,
          "Unable to render template:\n" + contents + "\n" + e.getMessage(),
          e);
    }
  }
}
