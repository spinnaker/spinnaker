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

package com.netflix.spinnaker.halyard.model.v1.providers;

import com.netflix.spinnaker.halyard.model.v1.Updateable;
import com.netflix.spinnaker.halyard.validate.v1.ValidateAccount;
import com.netflix.spinnaker.halyard.validate.v1.ValidateField;
import com.netflix.spinnaker.halyard.validate.v1.Validator;
import com.netflix.spinnaker.halyard.validate.v1.providers.ValidateAccountName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@EqualsAndHashCode(callSuper = false)
public class Account implements Cloneable, Updateable {
  @ValidateField(validators = {ValidateAccountName.class})
  String name;

  public List<String> validate() {
    List<String> errors = new ArrayList<>();

    Account account = this;
    Class aClass = this.getClass();

    errors.addAll(Arrays.stream(aClass.getDeclaredAnnotations())
        .filter(c -> c instanceof ValidateAccount)                             // Find all ValidateAccount annotations
        .map(v -> (ValidateAccount) v)
        .map(ValidateAccount::validators)                                      // Pick of the validators
        .flatMap(Stream::of)                                                   // Flatten the stream of lists
        .map(v -> {
          try {
            return v.getConstructor(this.getClass()).newInstance(this);        // Construct the validators
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }).filter(v -> !v.skip())                                              // Ignore skippable validators
        .flatMap(Validator::validate)                                          // Run the validators & flatten results
        .map(s -> String.format("Invalid account \"%s\": %s", account.getName(), s))
        .collect(Collectors.toList()));

    return errors;
  }
}
