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

import java.util.ArrayList;
import java.util.List;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;

public class AnsiParagraphBuilder {
  @Setter int indentWidth = 0;

  @Setter int maxLineWidth = 80;

  @Setter boolean indentFirstLine = true;

  List<AnsiSnippet> snippets = new ArrayList<>();

  String getIndent() {
    return StringUtils.leftPad("", indentWidth);
  }

  public AnsiSnippet addSnippet(String text) {
    AnsiSnippet snippet = new AnsiSnippet(text);
    snippets.add(snippet);
    return snippet;
  }

  @Override
  public String toString() {
    StringBuilder bodyBuilder = new StringBuilder();

    for (AnsiSnippet snippet : snippets) {
      bodyBuilder.append(snippet.toString());
    }

    String body = bodyBuilder.toString();

    if (maxLineWidth < 0) {
      return body;
    }

    String[] lines = body.split("\n");

    StringBuilder lineBuilder = new StringBuilder();

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];

      line = WordUtils.wrap(line, maxLineWidth, "\n" + getIndent(), false);

      if (indentFirstLine) {
        line = getIndent() + line;
      }

      lineBuilder.append(line);

      if (i != lines.length - 1) {
        lineBuilder.append("\n");
      }
    }

    return lineBuilder.toString();
  }
}
