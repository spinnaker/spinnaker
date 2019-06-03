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

public enum AnsiForegroundColor implements AnsiCode {
  BLACK("\033[30m"),
  RED("\033[31m"),
  GREEN("\033[32m"),
  YELLOW("\033[33m"),
  BLUE("\033[34m"),
  MAGENTA("\033[35m"),
  CYAN("\033[36m"),
  WHITE("\033[37m");

  @Getter final String code;

  AnsiForegroundColor(String code) {
    this.code = code;
  }
}
