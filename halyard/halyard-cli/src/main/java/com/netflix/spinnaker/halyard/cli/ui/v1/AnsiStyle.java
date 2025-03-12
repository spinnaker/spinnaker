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

public enum AnsiStyle implements AnsiCode {
  BOLD("\033[1m"),
  ITALICS("\033[3m"),
  UNDERLINE("\033[4m"),
  INVERSE("\033[7m"),
  STRIKETHROUGH("\033[9m");

  @Getter final String code;

  AnsiStyle(String code) {
    this.code = code;
  }
}
