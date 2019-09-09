/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.test.mimicker.producers;

import com.netflix.spinnaker.kork.test.mimicker.DataContainer;
import java.util.StringJoiner;

public class TextProducer {

  private final DataContainer dataContainer;
  private final RandomProducer randomProducer;

  public TextProducer(DataContainer dataContainer, RandomProducer randomProducer) {
    this.dataContainer = dataContainer;
    this.randomProducer = randomProducer;
  }

  public String word() {
    return dataContainer.random("words");
  }

  public String dashedWords(int count) {
    StringJoiner joiner = new StringJoiner("-");
    for (int i = 0; i < count; i++) {
      joiner.add(dataContainer.random("words"));
    }
    return joiner.toString();
  }

  public String dashedWords(int minCount, int maxCount) {
    return dashedWords(randomProducer.intValue(minCount, maxCount));
  }
}
