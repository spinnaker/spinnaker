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

import spock.lang.Specification;

public class AccountSpec extends Specification {
  final static String ORIGINAL_NAME = "a-name"
  final static String GOOD_NAME = "good-name"
  final static String BAD_NAME_SPACE = "bad name"
  final static String BAD_NAME_CHARACTER = "!bad-name"

  void "account name passes validation"() {
    when:
    def account = new Account(name: ORIGINAL_NAME);
    def errors = account.update("name", GOOD_NAME, String.class)

    then:
    errors == []
    account.name == GOOD_NAME
  }

  void "account name fails validation when null"() {
    when:
    def account = new Account(name: ORIGINAL_NAME);
    def errors = account.update("name", null, String.class)

    then:
    errors[0].contains("null")
    account.name == ORIGINAL_NAME
  }

  void "account name fails validation with space"() {
    when:
    def account = new Account(name: ORIGINAL_NAME);
    def errors = account.update("name", BAD_NAME_SPACE, String.class)

    then:
    errors[0].contains("Must consist of")
    account.name == ORIGINAL_NAME
  }

  void "account name fails validation with illegal character"() {
    when:
    def account = new Account(name: ORIGINAL_NAME);
    def errors = account.update("name", BAD_NAME_CHARACTER, String.class)

    then:
    errors[0].contains("Must consist of")
    account.name == ORIGINAL_NAME
  }
}
