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

public class AnsiStoryBuilder {
  List<AnsiParagraphBuilder> paragraphs = new ArrayList<>();

  public AnsiParagraphBuilder addParagraph() {
    AnsiParagraphBuilder paragraph = new AnsiParagraphBuilder();
    paragraphs.add(paragraph);
    return paragraph;
  }

  public void addNewline() {
    AnsiParagraphBuilder paragraph = new AnsiParagraphBuilder();
    paragraph.addSnippet("");
    paragraphs.add(paragraph);
  }

  @Override
  public String toString() {
    StringBuilder res = new StringBuilder();

    for (AnsiParagraphBuilder paragraph : paragraphs) {
      res.append(paragraph.toString()).append("\n");
    }

    return res.toString();
  }
}
