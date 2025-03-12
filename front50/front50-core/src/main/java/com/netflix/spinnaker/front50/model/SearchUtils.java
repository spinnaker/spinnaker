/*
 * Copyright 2016 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.front50.model;

import com.netflix.spinnaker.front50.UntypedUtils;
import com.netflix.spinnaker.front50.api.model.Timestamped;
import java.util.Map;
import java.util.TreeMap;

public class SearchUtils {

  public static boolean matchesIgnoreCase(Map<String, String> source, String key, String value) {
    Map<String, String> treeMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    treeMap.putAll(source);
    return treeMap.containsKey(key) && treeMap.get(key).toLowerCase().contains(value.toLowerCase());
  }

  public static int score(final Timestamped timestamped, Map<String, String> attributes) {
    return attributes.entrySet().stream()
        .map(e -> score(timestamped, e.getKey(), e.getValue()))
        .reduce(Integer::sum)
        .orElse(0);
  }

  public static int score(Timestamped timestamped, String attributeName, String attributeValue) {
    Map<String, String> treeMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    treeMap.putAll(UntypedUtils.getProperties(timestamped));

    if (!treeMap.containsKey(attributeName)) {
      return 0;
    }

    String attribute = treeMap.get(attributeName).toLowerCase();
    int indexOf = attribute.indexOf(attributeValue.toLowerCase());

    // what percentage of the value matched
    double coverage = ((double) attributeValue.length() / attribute.length()) * 100;

    // where did the match occur, bonus points for it occurring close to the start
    int boost = attribute.length() - indexOf;

    // scale boost based on coverage percentage
    double scaledBoost = (coverage / 100) * boost;

    return (int) (indexOf < 0 ? 0 : coverage + scaledBoost);
  }
}
