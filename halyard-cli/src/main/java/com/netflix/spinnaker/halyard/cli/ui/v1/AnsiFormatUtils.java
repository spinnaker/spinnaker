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
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

public class AnsiFormatUtils {
  private static Yaml yamlParser = null;
  private static ObjectMapper objectMapper = null;

  private static Yaml getYamlParser() {
    if (yamlParser == null) {
      DumperOptions options = new DumperOptions();
      options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
      options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

      yamlParser = new Yaml(options);
    }

    return yamlParser;
  }

  private  static ObjectMapper getObjectMapper() {
    if (objectMapper == null) {
      objectMapper = new ObjectMapper();
    }

    return objectMapper;
  }

  public static String formatYaml(Object yaml) {
    return getYamlParser().dump(getObjectMapper().convertValue(yaml, Map.class));
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
}
