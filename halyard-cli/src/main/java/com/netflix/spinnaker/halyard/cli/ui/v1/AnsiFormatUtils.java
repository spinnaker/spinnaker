/*
 * Copyright 2017 Google, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeDiff;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

public class AnsiFormatUtils {
  private static Yaml yamlParser = null;
  private static ObjectMapper objectMapper = null;

  public enum Format  {
    YAML,
    STRING,
    NONE
  }

  private static Yaml getYamlParser() {
    if (yamlParser == null) {
      DumperOptions options = new DumperOptions();
      options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
      options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

      yamlParser = new Yaml(options);
    }

    return yamlParser;
  }

  private static ObjectMapper getObjectMapper() {
    if (objectMapper == null) {
      objectMapper = new ObjectMapper();
    }

    return objectMapper;
  }

  public static String formatYaml(Object yaml) {
    return getYamlParser().dump(getObjectMapper().convertValue(yaml, Map.class));
  }

  public static String format(Format format, Object o) {
    if (o == null) {
      return "";
    }

    switch (format) {
      case NONE:
        return "";
      case STRING:
        return o.toString();
      case YAML:
        return formatYaml(o);
      default:
        throw new RuntimeException("Unknown format: " + format);
    }
  }

  public static String format(Account account) {
    AnsiStoryBuilder resultBuilder = new AnsiStoryBuilder();
    AnsiParagraphBuilder paragraph = resultBuilder.addParagraph();

    paragraph.addSnippet(account.getNodeName().toUpperCase()).addStyle(AnsiStyle.BOLD);

    resultBuilder.addNewline();

    paragraph = resultBuilder.addParagraph();
    paragraph.addSnippet(account.toString());

    return resultBuilder.toString();
  }

  public static String format(Provider provider) {
    AnsiStoryBuilder resultBuilder = new AnsiStoryBuilder();
    AnsiParagraphBuilder paragraph = resultBuilder.addParagraph();

    paragraph.addSnippet(provider.getNodeName().toUpperCase()).addStyle(AnsiStyle.BOLD);
    paragraph.addSnippet(" provider");

    resultBuilder.addNewline();

    paragraph = resultBuilder.addParagraph();

    paragraph.addSnippet("enabled: " + provider.isEnabled());

    paragraph = resultBuilder.addParagraph();
    paragraph.addSnippet("accounts: ");

    List<Account> accounts = provider.getAccounts();
    if (accounts == null || accounts.isEmpty()) {
      paragraph.addSnippet("[]");
    } else {
      accounts.forEach(account -> {
        AnsiParagraphBuilder list = resultBuilder.addParagraph().setIndentFirstLine(true).setIndentWidth(1);
        list.addSnippet("- ");
        list.addSnippet(account.getName());
      });
    }

    return resultBuilder.toString();
  }

  public static String format(NodeDiff diff) {
    AnsiStoryBuilder resultBuilder = new AnsiStoryBuilder();
    format(diff, resultBuilder);
    return resultBuilder.toString();
  }

  static void format(NodeDiff diff, AnsiStoryBuilder resultBuilder) {

    AnsiSnippet snippet = null;
    AnsiParagraphBuilder paragraph = null;
    boolean printLocation = true;
    switch (diff.getChangeType()) {
      case EDITED:
        if (!diff.getFieldDiffs().isEmpty()) {
          snippet = new AnsiSnippet("~ EDITED\n").setForegroundColor(AnsiForegroundColor.MAGENTA);
        } else {
          printLocation = false;
        }
        break;
      case REMOVED:
        snippet = new AnsiSnippet("- REMOVED\n").setForegroundColor(AnsiForegroundColor.RED);
        break;
      case ADDED:
        snippet = new AnsiSnippet("+ ADDED\n").setForegroundColor(AnsiForegroundColor.GREEN);
        break;
      default:
        throw new RuntimeException("Unknown changetype " + diff.getChangeType());
    }

    if (printLocation) {
      paragraph = resultBuilder.addParagraph();
      paragraph.addSnippet(snippet.addStyle(AnsiStyle.BOLD).toString());
      paragraph.addSnippet(diff.getLocation()).addStyle(AnsiStyle.BOLD);
    }

    for (NodeDiff.FieldDiff fieldDiff : diff.getFieldDiffs()) {
      paragraph = resultBuilder.addParagraph();
      paragraph.addSnippet(" - ");
      paragraph.addSnippet(fieldDiff.getFieldName()).addStyle(AnsiStyle.UNDERLINE);
      paragraph.addSnippet(" " + fieldDiff.getOldValue() + " -> " + fieldDiff.getNewValue());
    }

    if (printLocation) {
      resultBuilder.addNewline();
    }

    for (NodeDiff nodeDiff : diff.getNodeDiffs()) {
      format(nodeDiff, resultBuilder);
    }
  }
}
