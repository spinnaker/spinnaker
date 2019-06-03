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
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

public class AnsiMessageList {
  @Getter List<AnsiSnippet> messages = new ArrayList<>();

  private AnsiSnippet addIndentedMessage(int indentWidth, String messageText) {
    String indent = StringUtils.leftPad("", indentWidth);
    messages.add(new AnsiSnippet(indent));

    AnsiSnippet message = new AnsiSnippet(messageText);
    messages.add(message);

    return message;
  }

  public AnsiSnippet addMessage(String messageText) {
    return addIndentedMessage(0, messageText);
  }

  public void addNewline() {
    messages.add(new AnsiSnippet("\n"));
  }
}
