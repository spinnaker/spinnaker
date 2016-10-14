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

package com.netflix.spinnaker.halyard.validate.v1.providers;

import com.netflix.spinnaker.halyard.validate.v1.Validator;

import java.io.*;
import java.util.stream.Stream;

/**
 * Ensure that the given file exists, and is readable.
 */
public class ValidateFileExists extends Validator<String> {
  protected ValidateFileExists(String subject) {
    super(subject);
  }

  @Override
  public Stream<String> validate() {
    try {
      BufferedReader reader = new BufferedReader(new FileReader(new File(subject)));
      reader.read();
    } catch (FileNotFoundException e) {
      return Stream.of(String.format("File \"%s\" not found.", subject));
    } catch (IOException e) {
      return Stream.of(String.format("Unable to read from file \"%s\".", subject));
    }
    return null;
  }

  @Override
  public boolean skip() {
    // It's OK to remove a reference to a file.
    return (subject == null || subject.isEmpty());
  }
}
