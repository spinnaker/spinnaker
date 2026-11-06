/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.yaml;

import java.util.Map;
import java.util.Optional;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.resolver.Resolver;

final class Yaml11Scalars {
  private static final Resolver RESOLVER = new Resolver();

  private static final Map<String, String> LITERALS =
      Map.ofEntries(
          Map.entry("yes", "true"),
          Map.entry("Yes", "true"),
          Map.entry("YES", "true"),
          Map.entry("on", "true"),
          Map.entry("On", "true"),
          Map.entry("ON", "true"),
          Map.entry("True", "true"),
          Map.entry("TRUE", "true"),
          Map.entry("no", "false"),
          Map.entry("No", "false"),
          Map.entry("NO", "false"),
          Map.entry("off", "false"),
          Map.entry("Off", "false"),
          Map.entry("OFF", "false"),
          Map.entry("False", "false"),
          Map.entry("FALSE", "false"),
          Map.entry("~", "null"),
          Map.entry("Null", "null"),
          Map.entry("NULL", "null"));

  private Yaml11Scalars() {}

  static ScalarEvent normalize(ScalarEvent event) {
    String value = event.getValue();
    if (value.isEmpty()) {
      return event;
    }
    Tag tag = RESOLVER.resolve(NodeId.scalar, value, event.getImplicit().canOmitTagInPlainScalar());
    if (Tag.FLOAT.equals(tag)) {
      validateFraction(value);
    }
    return new ScalarEvent(
        event.getAnchor(),
        Optional.of(tag.getValue()),
        event.getImplicit(),
        LITERALS.getOrDefault(value, value),
        event.getScalarStyle(),
        event.getStartMark(),
        event.getEndMark());
  }

  private static void validateFraction(String value) {
    String cleaned = value.replace("_", "");
    int start = cleaned.startsWith("-") || cleaned.startsWith("+") ? 1 : 0;
    if (cleaned.charAt(start) != '.') {
      return;
    }
    int fraction = start + 1;
    if (fraction == cleaned.length()
        || cleaned.charAt(fraction) == 'e'
        || cleaned.charAt(fraction) == 'E') {
      throw new NumberFormatException(value);
    }
  }
}
