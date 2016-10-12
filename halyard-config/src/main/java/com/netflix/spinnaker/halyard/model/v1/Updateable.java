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

package com.netflix.spinnaker.halyard.model.v1;

import com.netflix.spinnaker.halyard.validate.v1.ValidateField;
import com.netflix.spinnaker.halyard.validate.v1.Validator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface Updateable {
  /* Attempts to update field to value, but will be rejected if the validation based on `context fails */
  default public List<String> update(String fieldName, Object value, Class<?> valueType) throws NoSuchFieldException, IllegalAccessException {
    Field field = this.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    Object oldValue = field.get(this);
    field.set(this, value);

    List<String> errors = Arrays.stream(field.getDeclaredAnnotations())
        .filter(c -> c instanceof ValidateField)                               // Find all ValidateField annotations
        .map(v -> (ValidateField) v)
        .map(ValidateField::validators)                                        // Pick of the validators
        .flatMap(Stream::of)                                                   // Flatten the stream of lists
        .map(v -> {
          try {
            return v.getConstructor(valueType).newInstance(value);             // Construct the validators
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }).filter(v -> !v.skip())                                              // Ignore skippable validators
        .flatMap(Validator::validate)                                          // Run the validators & flatten results
        .collect(Collectors.toList());

    // Reset value after failure
    if (!errors.isEmpty()) {
      field.set(this, oldValue);
    }

    field.setAccessible(false);

    return errors;
  }
}
