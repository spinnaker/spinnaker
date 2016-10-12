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

package com.netflix.spinnaker.halyard.model.v1

import com.netflix.spinnaker.halyard.validate.v1.ValidateField
import com.netflix.spinnaker.halyard.validate.v1.Validator
import lombok.Data
import spock.lang.Specification

import java.util.stream.Stream

public class UpdateableSpec extends Specification{
  static final String ORIGINAL_VALUE = "1"
  static final String GOOD_VALUE = "2"
  static final String SKIP_VALUE = "3"
  static final String BAD_VALUE = "."
  static final String ERROR_MESSAGE = "Bad value."

  void "should update my field name"() {
    when:
    def test = new Test(name: ORIGINAL_VALUE)
    def res = test.update("name", GOOD_VALUE, String.class)

    then:
    res == []
    test.name == GOOD_VALUE
  }

  void "should not update my field name"() {
    when:
    def test = new Test(name: ORIGINAL_VALUE)
    def res = test.update("name", BAD_VALUE, String.class)

    then:
    res == [ERROR_MESSAGE]
    test.name == ORIGINAL_VALUE
  }

  void "should update my field name due to skip"() {
    when:
    def test = new Test(name: ORIGINAL_VALUE)
    def res = test.update("name", SKIP_VALUE, String.class)

    then:
    res == []
    test.name == SKIP_VALUE
  }

  @Data
  public class Test implements Updateable {
    @ValidateField(validators = [TestValidator.class])
    String name
  }
}

public class TestValidator extends Validator<String> {
  public TestValidator(String value) {
    super(value)
  }

  @Override
  public Stream<String> validate() {
    if (subject == UpdateableSpec.GOOD_VALUE) {
      return null
    } else {
      return [UpdateableSpec.ERROR_MESSAGE].stream()
    }
  }

  @Override
  public boolean skip() {
    return (subject == UpdateableSpec.SKIP_VALUE)
  }
}
