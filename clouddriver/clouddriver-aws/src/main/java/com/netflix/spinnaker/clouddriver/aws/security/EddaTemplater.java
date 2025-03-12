/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.security;

import java.util.regex.Pattern;

public interface EddaTemplater {
  static EddaTemplater defaultTemplater() {
    return new PatternReplacementTemplater();
  }

  static EddaTemplater stringFormat() {
    return new StringFormatTemplater();
  }

  static EddaTemplater pattern() {
    return new PatternReplacementTemplater();
  }

  static EddaTemplater pattern(String pattern) {
    return new PatternReplacementTemplater(pattern);
  }

  String getUrl(String template, String region);

  class StringFormatTemplater implements EddaTemplater {
    public String getUrl(String template, String region) {
      return String.format(template, region);
    }
  }

  class PatternReplacementTemplater implements EddaTemplater {
    private final String replacementPattern;

    public PatternReplacementTemplater() {
      this("{{region}}");
    }

    public PatternReplacementTemplater(String replacementPattern) {
      this.replacementPattern = Pattern.quote(replacementPattern);
    }

    public String getUrl(String template, String region) {
      return template.replaceAll(replacementPattern, region);
    }
  }
}
