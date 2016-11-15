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

/**
 * A shared colored terminal output class
 */
public class AnsiUi {
  public static void raw(String message) {
    AnsiPrinter.println(new AnsiMessage(message));
  }

  public static void info(String message) {
    AnsiMessage prefix = new AnsiMessage(". ")
        .setForegroundColor(AnsiForegroundColor.BLUE)
        .addStyle(AnsiStyle.BOLD);

    prefixedPrint(prefix, message);
  }

  public static void warning(String message) {
    AnsiMessage prefix = new AnsiMessage("- ")
        .setForegroundColor(AnsiForegroundColor.YELLOW)
        .addStyle(AnsiStyle.BOLD);

    prefixedPrint(prefix, message);
  }

  public static void failure(String message) {
    AnsiMessage prefix = new AnsiMessage("! ")
        .setForegroundColor(AnsiForegroundColor.RED)
        .addStyle(AnsiStyle.BOLD);

    prefixedPrint(prefix, message);
  }

  public static void remediation(String message) {
    AnsiMessage prefix = new AnsiMessage("? ")
        .setForegroundColor(AnsiForegroundColor.MAGENTA)
        .addStyle(AnsiStyle.BOLD);

    prefixedPrint(prefix, message);
  }

  public static void success(String message) {
    AnsiMessage prefix = new AnsiMessage("+ ")
        .setForegroundColor(AnsiForegroundColor.BLUE)
        .addStyle(AnsiStyle.BOLD);

    prefixedPrint(prefix, message);
  }

  private static void prefixedPrint(AnsiMessage prefix, String message) {
    AnsiMessage body = new AnsiMessage(message);

    AnsiPrinter.println(prefix, body);
  }
}
