/*
 * Copyright 2021 Armory, Inc.
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
package com.netflix.spinnaker.front50.api.validator;

import com.netflix.spinnaker.kork.annotations.Alpha;
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import java.util.ArrayList;
import java.util.List;

/** A {@link ValidatorErrors} stores and reads multiple validation failure error messages */
@Alpha
public class ValidatorErrors implements SpinnakerExtensionPoint {
  private List<String> errors = new ArrayList<>();

  public List<String> getAllErrors() {
    return this.errors;
  }

  public String getAllErrorsMessage() {
    return String.join("\n", this.errors);
  }

  public Boolean hasErrors() {
    return !this.errors.isEmpty();
  }

  public void reject(String message) {
    this.errors.add(message);
  }
}
