/*
 * Copyright 2017 Armory, Inc.
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

package com.netflix.spinnaker.echo.notification;

import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

// this was copied and modified from
// https://github.com/jhy/jsoup/blob/jsoup-1.8.2/src/main/java/org/jsoup/examples/HtmlToPlainText.java
public class HtmlToPlainText {
  public String getPlainText(Element element) {
    FormattingVisitor formatter = new FormattingVisitor();
    NodeTraversor traversor = new NodeTraversor(formatter);
    traversor.traverse(element);
    return formatter.toString();
  }

  // the formatting rules, implemented in a breadth-first DOM traverse
  private static class FormattingVisitor implements NodeVisitor {
    private StringBuilder accum = new StringBuilder(); // holds the accumulated text

    // hit when the node is first seen
    public void head(Node node, int depth) {
      String name = node.nodeName();
      if (node instanceof TextNode) {
        append(((TextNode) node).text()); // TextNodes carry all user-readable text in the DOM.
      } else if (name.equals("li")) {
        append("\n * ");
      } else if (name.equals("dt")) {
        append("  ");
      } else if (StringUtil.in(name, "p", "h1", "h2", "h3", "h4", "h5", "tr")) {
        append("\n");
      }
    }

    // hit when all of the node's children (if any) have been visited
    public void tail(Node node, int depth) {
      String name = node.nodeName();
      if (StringUtil.in(name, "br", "dd", "dt", "p", "h1", "h2", "h3", "h4", "h5")) {
        append("\n");
      } else if (name.equals("a")) {
        append(String.format(" <%s>", node.absUrl("href")));
      }
    }

    // appends text to the string builder
    private void append(String text) {
      if (text.equals(" ")
          && (accum.length() == 0
              || StringUtil.in(accum.substring(accum.length() - 1), " ", "\n"))) {
        return; // don't accumulate long runs of empty spaces
      }

      accum.append(text);
    }

    @Override
    public String toString() {
      return accum.toString();
    }
  }
}
