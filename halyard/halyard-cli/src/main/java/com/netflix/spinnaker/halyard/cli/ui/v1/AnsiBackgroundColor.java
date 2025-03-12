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

package com.netflix.spinnaker.halyard.cli.ui.v1;

import lombok.Getter;

public enum AnsiBackgroundColor implements AnsiCode {
  BLACK("\033[40m"),
  RED("\033[41m"),
  GREEN("\033[42m"),
  YELLOW("\033[43m"),
  BLUE("\033[44m"),
  MAGENTA("\033[45m"),
  CYAN("\033[46m"),
  WHITE("\033[47m");

  @Getter final String code;

  AnsiBackgroundColor(String code) {
    this.code = code;
  }
}
