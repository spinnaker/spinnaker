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
 * Ensure given value is not null.
 */
public class ValidateNotNull extends Validator<Object> {
  protected ValidateNotNull(Object subject) {
    super(subject);
  }

  @Override
  public Stream<String> validate() {
    if (subject == null) {
      return Stream.of("Must not be null");
    } else {
      return null;
    }
  }

  @Override
  public boolean skip() {
    return false;
  }
}
