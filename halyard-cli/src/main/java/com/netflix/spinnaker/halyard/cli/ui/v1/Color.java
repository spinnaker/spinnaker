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

public class Color {
  public final String RESET;
  public final String BOLD;
  public final String RED;
  public final String GREEN;
  public final String YELLOW;
  public final String BLUE;
  public final String MAGENTA;

  public Color (boolean colorEnabled) {
    RESET = colorEnabled ? "\033[0m" : "";
    BOLD = colorEnabled ? "\033[1m": "";
    RED = colorEnabled ? "\033[31m": "";
    GREEN = colorEnabled ? "\033[32m": "";
    YELLOW = colorEnabled ? "\033[33m": "";
    BLUE = colorEnabled ? "\033[34m": "";
    MAGENTA = colorEnabled ? "\033[35m": "";
  }
}
