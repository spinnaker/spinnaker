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

@Component
public class ActuatorSanitizingFunction implements SanitizingFunction {

  private static final String[] REGEX_PARTS = {"*", "$", "^", "+"};
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
  private static final Set<String> URI_USERINFO_KEYS =
      Set.of("uri", "uris", "url", "urls", "address", "addresses");
  private static final Pattern URI_USERINFO_PATTERN =
      Pattern.compile("^\\[?[A-Za-z][A-Za-z0-9\\+\\.\\-]+://.+:(.*)@.+$");
  private List<Pattern> keysToSanitize = new ArrayList<>();

  public ActuatorSanitizingFunction(List<String> additionalKeysToSanitize) {
    addKeysToSanitize(DEFAULT_KEYS_TO_SANITIZE);
    addKeysToSanitize(URI_USERINFO_KEYS);
    addKeysToSanitize(additionalKeysToSanitize);
  }

  public ActuatorSanitizingFunction() {
    addKeysToSanitize(DEFAULT_KEYS_TO_SANITIZE);
    addKeysToSanitize(URI_USERINFO_KEYS);
  }

  private void addKeysToSanitize(Collection<String> keysToSanitize) {
    for (String key : keysToSanitize) {
      this.keysToSanitize.add(getPattern(key));
    }
  }

  private Pattern getPattern(String value) {
    if (isRegex(value)) {
      return Pattern.compile(value, Pattern.CASE_INSENSITIVE);
    }
    return Pattern.compile(".*" + value + "$", Pattern.CASE_INSENSITIVE);
  }

  private boolean isRegex(String value) {
    for (String part : REGEX_PARTS) {
      if (value.contains(part)) {
        return true;
      }
    }
    return false;
  }

  public void setKeysToSanitize(String... keysToSanitize) {
    if (keysToSanitize != null) {
      for (String key : keysToSanitize) {
        this.keysToSanitize.add(getPattern(key)); // todo: clear oll existing the make the list.
      }
    }
  }

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

  private boolean keyIsUriWithUserInfo(Pattern pattern) {
    for (String uriKey : URI_USERINFO_KEYS) {
      if (pattern.matcher(uriKey).matches()) {
        return true;
      }
    }
    return false;
  }

  private Object sanitizeUris(String value) {
    return Arrays.stream(value.split(",")).map(this::sanitizeUri).collect(Collectors.joining(","));
  }

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
