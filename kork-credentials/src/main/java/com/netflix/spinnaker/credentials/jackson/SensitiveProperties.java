/*
 * Copyright 2022 Apple Inc.
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

package com.netflix.spinnaker.credentials.jackson;

import java.util.regex.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("credentials.masking")
@Data
public class SensitiveProperties {
  /** Configure the pattern to use for matching likely sensitive property names. */
  // note: `(?i)` makes the rest of the pattern case-insensitive
  // https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/regex/Pattern.html#CASE_INSENSITIVE
  private Pattern sensitivePropertyNamePattern =
      Pattern.compile("(?i)pass(word|phrase)|(api|access)?token|.*(private|secret|access)key.*");
}
