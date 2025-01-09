/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.echo.googlechat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GoogleChatMessage {
  // TODO: Make the Spinnaker info configurable to make reskinning viable.
  static transient String SPINNAKER_ICON_URL =
      "https://avatars0.githubusercontent.com/u/7634182?s=200&v=4";
  static transient String SPINNAKER_FRONT_PAGE_URL = "https://www.spinnaker.io/";

  public Card cards;

  transient String message;

  public GoogleChatMessage(String message) {
    this.message = message;
    cards = new Card();
  }

  /** Classes below are used to build the JSON object for the Chat API. * */
  class Card {
    public List<Section> sections = new ArrayList<>();

    public Card() {
      sections.add(new Section());
    }
  }

  class Section {
    public List<Object> widgets = new ArrayList<>();

    public Section() {
      widgets.add(new TextParagraphWidget());
      widgets.add(new ButtonWidget());
    }
  }

  class TextParagraphWidget {
    public HashMap<String, String> textParagraph = new HashMap<>();

    public TextParagraphWidget() {
      textParagraph.put("text", message);
    }
  }

  class ButtonWidget {
    public List<Object> buttons = new ArrayList<>();

    public ButtonWidget() {
      buttons.add(new ImageButtonWidget());
      buttons.add(new TextButtonWidget());
    }
  }

  class ImageButtonWidget {
    public ImageButton imageButton = new ImageButton();
  }

  class ImageButton {
    public String iconUrl = SPINNAKER_ICON_URL;
    public OnClick onClick = new OnClick(SPINNAKER_FRONT_PAGE_URL);
  }

  class TextButtonWidget {
    public TextButton textButton = new TextButton();
  }

  class TextButton {
    public String text = "From Spinnaker";
    public OnClick onClick = new OnClick(SPINNAKER_FRONT_PAGE_URL);
  }

  class OnClick {
    public OpenLink openLink;

    public OnClick(String link) {
      openLink = new OpenLink(link);
    }
  }

  class OpenLink {
    public String url;

    public OpenLink(String link) {
      url = link;
    }
  }
}
