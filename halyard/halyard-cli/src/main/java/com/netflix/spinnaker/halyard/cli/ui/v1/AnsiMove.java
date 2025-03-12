/*
 * Copyright 2017 Google, Inc.
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
 */

package com.netflix.spinnaker.halyard.cli.ui.v1;

import lombok.Setter;

public enum AnsiMove implements AnsiCode {
  UP("\033[%dA"),
  DOWN("\033[%dB"),
  FORWARD("\033[%dC"),
  BACKWARD("\033[%dD");

  private final String code;

  @Override
  public String getCode() {
    if (count == null) {
      throw new IllegalStateException("Code value cannot be read until count is non-null");
    }

    if (count < 0) {
      throw new IllegalStateException("Code value must not be negative");
    }

    return String.format(code, count);
  }

  @Setter private Integer count;

  AnsiMove(String code) {
    this.code = code;
  }
}
