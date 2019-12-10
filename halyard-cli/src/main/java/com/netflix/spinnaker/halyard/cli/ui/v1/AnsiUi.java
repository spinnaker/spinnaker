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

/** A shared colored terminal output class */
public class AnsiUi {
  public static void raw(String message) {
    AnsiPrinter.out.println(new AnsiSnippet(message).toString());
  }

  public static void listItem(String message) {
    AnsiParagraphBuilder builder =
        new AnsiParagraphBuilder().setIndentFirstLine(false).setIndentWidth(4);

    builder.addSnippet("  - ").addStyle(AnsiStyle.BOLD);

    builder.addSnippet(message);

    AnsiPrinter.out.println(builder.toString());
  }

  public static void problemLocation(String message) {
    AnsiParagraphBuilder builder =
        new AnsiParagraphBuilder().setIndentFirstLine(false).setIndentWidth(2);

    builder.addSnippet("Validation in ");

    builder.addSnippet(message).addStyle(AnsiStyle.BOLD);

    builder.addSnippet(":");

    AnsiPrinter.err.println(builder.toString());
  }

  public static void info(String message) {
    AnsiParagraphBuilder builder =
        new AnsiParagraphBuilder().setIndentFirstLine(false).setIndentWidth(2);

    builder
        .addSnippet("- INFO ")
        .setForegroundColor(AnsiForegroundColor.GREEN)
        .addStyle(AnsiStyle.BOLD);

    builder.addSnippet(message);

    AnsiPrinter.out.println(builder.toString());
  }

  public static void warning(String message) {
    AnsiParagraphBuilder builder =
        new AnsiParagraphBuilder().setIndentFirstLine(false).setIndentWidth(2);

    builder
        .addSnippet("- WARNING ")
        .setForegroundColor(AnsiForegroundColor.YELLOW)
        .addStyle(AnsiStyle.BOLD);

    builder.addSnippet(message);

    AnsiPrinter.err.println(builder.toString());
  }

  public static void error(String message) {
    AnsiParagraphBuilder builder =
        new AnsiParagraphBuilder().setIndentFirstLine(false).setIndentWidth(2);

    builder
        .addSnippet("! ERROR ")
        .setForegroundColor(AnsiForegroundColor.RED)
        .addStyle(AnsiStyle.BOLD);

    builder.addSnippet(message);

    AnsiPrinter.err.println(builder.toString());
  }

  public static void remediation(String message) {
    AnsiParagraphBuilder builder =
        new AnsiParagraphBuilder().setIndentFirstLine(false).setIndentWidth(2);

    builder
        .addSnippet("? ")
        .setForegroundColor(AnsiForegroundColor.MAGENTA)
        .addStyle(AnsiStyle.BOLD);

    builder.addSnippet(message);

    AnsiPrinter.err.println(builder.toString());
  }

  public static void listRemediation(String message) {
    AnsiParagraphBuilder builder =
        new AnsiParagraphBuilder().setIndentFirstLine(false).setIndentWidth(4);

    builder.addSnippet("  - ").addStyle(AnsiStyle.BOLD);

    builder.addSnippet(message);

    AnsiPrinter.err.println(builder.toString());
  }

  public static void success(String message) {
    AnsiParagraphBuilder builder =
        new AnsiParagraphBuilder().setIndentFirstLine(false).setIndentWidth(2);

    builder.addSnippet("+ ").setForegroundColor(AnsiForegroundColor.GREEN).addStyle(AnsiStyle.BOLD);

    builder.addSnippet(message);

    AnsiPrinter.out.println(builder.toString());
  }

  public static void failure(String message) {
    AnsiParagraphBuilder builder =
        new AnsiParagraphBuilder().setIndentFirstLine(false).setIndentWidth(2);

    builder.addSnippet("- ").setForegroundColor(AnsiForegroundColor.RED).addStyle(AnsiStyle.BOLD);

    builder.addSnippet(message);

    AnsiPrinter.err.println(builder.toString());
  }
}
