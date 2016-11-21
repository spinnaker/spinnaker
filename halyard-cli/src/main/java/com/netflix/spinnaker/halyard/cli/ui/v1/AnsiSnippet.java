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

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AnsiSnippet {
  private AnsiForegroundColor foregroundColor;
  private AnsiBackgroundColor backgroundColor;
  private List<AnsiStyle> styles = new ArrayList<>();
  private String message;

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();

    if (backgroundColor != null) {
      result.append(backgroundColor.format());
    }

    if (foregroundColor != null) {
      result.append(foregroundColor.format());
    }

    if (styles != null) {
      for (AnsiStyle style : styles) {
        result.append(style.format());
      }
    }

    result.append(message);

    result.append(AnsiSpecial.RESET.format());

    return result.toString();
  }

  public AnsiSnippet addStyle(AnsiStyle style) {
    styles.add(style);
    return this;
  }

  public AnsiSnippet(String message) {
    this.message = message;
  }
}
