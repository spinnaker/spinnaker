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
package com.netflix.spinnaker.igor.docker;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DockerRegistryV2Key {
  private static final String VERSION = "v2";
  private static Pattern V2_PATTERN = Pattern.compile("^.*?:.*?:v2:.*?:.*?:.*$");
  private final String prefix;
  private final String id;
  private final String account;
  private final String registry;
  private final String tag;

  public DockerRegistryV2Key(
      String prefix, String id, String account, String registry, String tag) {
    this.prefix = prefix;
    this.id = id;
    this.account = account;
    this.registry = registry;
    this.tag = tag;
  }

  public static String getVersion() {
    return VERSION;
  }

  public String getPrefix() {
    return prefix;
  }

  public String getId() {
    return id;
  }

  public String getAccount() {
    return account;
  }

  public String getRegistry() {
    return registry;
  }

  public String getTag() {
    return tag;
  }

  public String toString() {
    return String.format("%s:%s:v2:%s:%s:%s", prefix, id, account, registry, tag);
  }

  public static boolean isV2Key(String key) {
    if (key == null) {
      return false;
    }
    Matcher matcher = V2_PATTERN.matcher(key);
    return matcher.matches();
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof DockerRegistryV2Key && toString().equals(obj.toString());
  }
}
