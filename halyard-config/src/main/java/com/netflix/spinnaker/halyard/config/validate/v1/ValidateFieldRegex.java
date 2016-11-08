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

import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Ensure the given value matches the input regex.
 */
public class ValidateFieldRegex extends Validator<String> {
  final String pattern;
  final String patternDescription;

  protected ValidateFieldRegex(String subject, String pattern, String patternDescription) {
    super(subject);
    this.pattern = pattern;
    this.patternDescription = patternDescription;
  }

  @Override
  public Stream<String> validate() {
    boolean matches = Pattern.matches(pattern, subject);
    if (!matches) {
      return Stream.of(String.format("%s (\"%s\" failed to match \"%s\")", patternDescription, subject, pattern));
    } else {
      return null;
    }
  }

  @Override
  public boolean skip() {
    return false;
  }
}
