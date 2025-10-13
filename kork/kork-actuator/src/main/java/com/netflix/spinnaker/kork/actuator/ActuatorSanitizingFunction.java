/*
 * Copyright 2024 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.kork.actuator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * {@code ActuatorSanitizingFunction} is a custom implementation of {@link SanitizingFunction} that
 * masks sensitive values exposed through Spring Boot Actuator endpoints (e.g., environment, config
 * props, etc.).
 *
 * <p>This class extends Spring’s sanitization mechanism by:
 *
 * <ul>
 *   <li>Providing default patterns for sensitive keys (e.g., {@code password}, {@code secret},
 *       {@code token}).
 *   <li>Supporting custom keys that can be added via constructor or setter.
 *   <li>Detecting and sanitizing credentials embedded in URI-like values (e.g., {@code
 *       jdbc://user:pass@host}).
 * </ul>
 *
 * <p>The sanitization replaces sensitive values with {@link SanitizableData#SANITIZED_VALUE}.
 */
@Component
public class ActuatorSanitizingFunction implements SanitizingFunction {

  private static final String[] REGEX_PARTS = {"*", "$", "^", "+"};

  /** Default keys or regex patterns that should be sanitized. */
  private static final Set<String> DEFAULT_KEYS_TO_SANITIZE =
      Set.of(
          "password",
          "secret",
          "key",
          "token",
          ".*credentials.*",
          "vcap_services",
          "^vcap\\.services.*$",
          "sun.java.command",
          "^spring[._]application[._]json$");

  /** Keys that are expected to contain URI values where credentials may appear. */
  private static final Set<String> URI_USERINFO_KEYS =
      Set.of("uri", "uris", "url", "urls", "address", "addresses");

  /** Pattern to detect user-info (username:password@) within URIs. */
  private static final Pattern URI_USERINFO_PATTERN =
      Pattern.compile("^\\[?[A-Za-z][A-Za-z0-9\\+\\.\\-]+://.+:(.*)@.+$");

  /** Compiled patterns representing keys to sanitize. */
  private final List<Pattern> keysToSanitize = new ArrayList<>();

  /**
   * Constructs an {@code ActuatorSanitizingFunction} with default and additional keys to sanitize.
   *
   * @param additionalKeysToSanitize additional keys (or regex patterns) to include for sanitization
   */
  public ActuatorSanitizingFunction(List<String> additionalKeysToSanitize) {
    addKeysToSanitize(DEFAULT_KEYS_TO_SANITIZE);
    addKeysToSanitize(URI_USERINFO_KEYS);
    addKeysToSanitize(additionalKeysToSanitize);
  }

  /** Constructs an {@code ActuatorSanitizingFunction} with only the default sanitization keys. */
  public ActuatorSanitizingFunction() {
    addKeysToSanitize(DEFAULT_KEYS_TO_SANITIZE);
    addKeysToSanitize(URI_USERINFO_KEYS);
  }

  /**
   * Adds the given collection of key patterns to the sanitization list.
   *
   * @param keysToSanitize a collection of key names or regex patterns
   */
  private void addKeysToSanitize(Collection<String> keysToSanitize) {
    for (String key : keysToSanitize) {
      this.keysToSanitize.add(getPattern(key));
    }
  }

  /**
   * Builds a {@link Pattern} for a given key. If the key appears to be a regex (contains regex
   * symbols), it is used as-is. Otherwise, it is treated as a simple suffix match
   * (case-insensitive).
   *
   * @param value key or regex string
   * @return compiled {@link Pattern} for matching keys
   */
  private Pattern getPattern(String value) {
    if (isRegex(value)) {
      return Pattern.compile(value, Pattern.CASE_INSENSITIVE);
    }
    return Pattern.compile(".*" + value + "$", Pattern.CASE_INSENSITIVE);
  }

  /**
   * Determines whether a given value should be interpreted as a regular expression.
   *
   * @param value string to inspect
   * @return {@code true} if the string contains regex-related symbols; {@code false} otherwise
   */
  private boolean isRegex(String value) {
    for (String part : REGEX_PARTS) {
      if (value.contains(part)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Replaces the existing sanitization patterns with the specified keys.
   *
   * @param keysToSanitize one or more keys (or regex patterns) to use for sanitization
   */
  public void setKeysToSanitize(String... keysToSanitize) {
    if (keysToSanitize != null) {
      for (String key : keysToSanitize) {
        this.keysToSanitize.add(getPattern(key)); // TODO: Clear existing list before replacing.
      }
    }
  }

  /**
   * Applies sanitization to a given {@link SanitizableData} instance.
   *
   * <p>If the key matches any configured pattern, its value is replaced with {@link
   * SanitizableData#SANITIZED_VALUE}. If the key refers to a URI and contains credentials, only the
   * password portion is masked.
   *
   * @param data the data to sanitize
   * @return sanitized version of the given {@code data}
   */
  @Override
  public SanitizableData apply(SanitizableData data) {
    if (data.getValue() == null) {
      return data;
    }

    for (Pattern pattern : keysToSanitize) {
      if (pattern.matcher(data.getKey()).matches()) {
        if (keyIsUriWithUserInfo(pattern)) {
          return data.withValue(sanitizeUris(data.getValue().toString()));
        }
        return data.withValue(SanitizableData.SANITIZED_VALUE);
      }
    }

    return data;
  }

  /**
   * Determines whether the provided pattern corresponds to a URI-like key where credentials might
   * appear.
   *
   * @param pattern key-matching pattern
   * @return {@code true} if the pattern matches a known URI key; otherwise {@code false}
   */
  private boolean keyIsUriWithUserInfo(Pattern pattern) {
    for (String uriKey : URI_USERINFO_KEYS) {
      if (pattern.matcher(uriKey).matches()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Sanitizes multiple comma-separated URI values.
   *
   * @param value comma-separated list of URIs
   * @return sanitized comma-separated URI list
   */
  private Object sanitizeUris(String value) {
    return Arrays.stream(value.split(",")).map(this::sanitizeUri).collect(Collectors.joining(","));
  }

  /**
   * Sanitizes a single URI value by masking any embedded password portion (e.g., {@code
   * user:****@host}).
   *
   * @param value URI string to sanitize
   * @return sanitized URI string
   */
  private String sanitizeUri(String value) {
    Matcher matcher = URI_USERINFO_PATTERN.matcher(value);
    String password = matcher.matches() ? matcher.group(1) : null;
    if (password != null) {
      return StringUtils.replace(
          value, ":" + password + "@", ":" + SanitizableData.SANITIZED_VALUE + "@");
    }
    return value;
  }
}
