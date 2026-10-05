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

import java.math.BigInteger;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import tools.jackson.core.StreamReadConstraints;

/**
 * Rewrites the plain YAML scalars that Jackson 2's YAML module read as booleans, nulls and numbers
 * but Jackson 3's (YAML 1.2, JSON schema) leaves as strings, into text Jackson 3 decodes the same
 * way. Quoted and tagged scalars never reach this class.
 *
 * <p>The rules were derived from, and are tested against, the output of a stock Jackson 2.21 {@code
 * YAMLMapper}: {@code yes/no/on/off}, {@code True/TRUE/False/FALSE}, {@code ~/Null/NULL},
 * leading-zero octal ({@code 0644}), {@code 0x}/{@code 0b} integers, underscores in numbers, a
 * leading {@code +}, and floats such as {@code .5}.
 */
final class Yaml11Scalars {

  private static final Pattern HEX = Pattern.compile("[-+]?0x[0-9a-fA-F_]+");
  private static final Pattern BINARY = Pattern.compile("[-+]?0b[01_]+");
  private static final Pattern OCTAL = Pattern.compile("[-+]?0[0-7_]+");
  private static final Pattern NUMBER =
      Pattern.compile("[-+]?(?:[0-9][0-9_]*)?\\.?[0-9_]*(?:[eE][-+]?[0-9_]+)?");

  /** What the Jackson 3 scalar resolver already decodes as an int or a float. */
  private static final Pattern JACKSON_NUMBER =
      Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?");

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

  /**
   * @return replacement text, or null when the scalar should be decoded as is
   */
  @Nullable
  static String normalize(String value, StreamReadConstraints constraints) {
    String literal = LITERALS.get(value);
    if (literal != null) {
      return literal;
    }
    return normalizeNumber(value, constraints);
  }

  @Nullable
  private static String normalizeNumber(String value, StreamReadConstraints constraints) {
    if (value.isEmpty()) {
      return null;
    }
    char first = value.charAt(0);
    if (!(Character.isDigit(first) || first == '+' || first == '-' || first == '.')) {
      return null;
    }
    if (HEX.matcher(value).matches()) {
      return integer(value, 2, 16, constraints);
    }
    if (BINARY.matcher(value).matches()) {
      return integer(value, 2, 2, constraints);
    }
    if (OCTAL.matcher(value).matches()) {
      return hasDigitAfterZero(value) ? integer(value, 1, 8, constraints) : null;
    }
    return normalizeDecimal(value);
  }

  @Nullable
  private static String normalizeDecimal(String value) {
    if (!NUMBER.matcher(value).matches() || !startsWithDigitOrDot(value)) {
      return null;
    }
    String cleaned = value.replace("_", "");
    if (cleaned.startsWith("+")) {
      cleaned = cleaned.substring(1);
    }
    // 00.5 and .5 are floats to Jackson 2; leading zeros before a point are dropped.
    cleaned = cleaned.replaceFirst("^(-?)0*(?=[0-9]*\\.)", "$1");
    cleaned = cleaned.replaceFirst("^(-?)\\.", "$1" + "0.");
    return !cleaned.equals(value) && JACKSON_NUMBER.matcher(cleaned).matches() ? cleaned : null;
  }

  private static boolean hasDigitAfterZero(String value) {
    int zero = value.indexOf('0');
    for (int i = zero + 1; i < value.length(); i++) {
      if (value.charAt(i) != '_') {
        return true;
      }
    }
    return false;
  }

  private static boolean startsWithDigitOrDot(String value) {
    int i = (value.charAt(0) == '-' || value.charAt(0) == '+') ? 1 : 0;
    return i < value.length() && (Character.isDigit(value.charAt(i)) || value.charAt(i) == '.');
  }

  private static String integer(
      String value, int prefixLength, int radix, StreamReadConstraints constraints) {
    boolean negative = value.charAt(0) == '-';
    int start = (value.charAt(0) == '-' || value.charAt(0) == '+') ? 1 : 0;
    String digits = value.substring(start + prefixLength).replace("_", "");
    if (radix == 8) {
      digits = value.substring(start).replace("_", "");
    }
    constraints.validateIntegerLength(digits.length());
    BigInteger parsed = new BigInteger(digits, radix);
    return (negative ? parsed.negate() : parsed).toString();
  }
}
