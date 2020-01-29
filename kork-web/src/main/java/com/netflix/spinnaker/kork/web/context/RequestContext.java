/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 */

package com.netflix.spinnaker.kork.web.context;

import com.netflix.spinnaker.kork.common.Header;
import java.util.Collection;
import java.util.Optional;

public interface RequestContext {
  default Optional<String> getAccounts() {
    return get(Header.ACCOUNTS);
  }

  default Optional<String> getUser() {
    return get(Header.USER);
  }

  default Optional<String> getUserOrigin() {
    return get(Header.USER_ORIGIN);
  }

  default Optional<String> getRequestId() {
    return get(Header.REQUEST_ID);
  }

  default Optional<String> getExecutionId() {
    return get(Header.EXECUTION_ID);
  }

  default Optional<String> getApplication() {
    return get(Header.APPLICATION);
  }

  default Optional<String> getExecutionType() {
    return get(Header.EXECUTION_TYPE);
  }

  default Optional<String> get(Header header) {
    return get(header.getHeader());
  }

  // setters
  default void setAccounts(Collection<String> accounts) {
    setAccounts(String.join(",", accounts));
  }

  default void setAccounts(String value) {
    set(Header.ACCOUNTS, value);
  }

  default void setUser(String value) {
    set(Header.USER, value);
  }

  default void setUserOrigin(String value) {
    set(Header.USER_ORIGIN, value);
  }

  default void setRequestId(String value) {
    set(Header.REQUEST_ID, value);
  }

  default void setExecutionId(String value) {
    set(Header.EXECUTION_ID, value);
  }

  default void setApplication(String value) {
    set(Header.APPLICATION, value);
  }

  default void setExecutionType(String value) {
    set(Header.EXECUTION_TYPE, value);
  }

  default void set(Header header, String value) {
    set(header.getHeader(), value);
  }

  // the only things that need to be overridden
  Optional<String> get(String header);

  void set(String header, String value);

  void clear();
}
