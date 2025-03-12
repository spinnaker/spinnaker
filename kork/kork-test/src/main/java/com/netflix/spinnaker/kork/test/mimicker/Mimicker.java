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
package com.netflix.spinnaker.kork.test.mimicker;

import com.netflix.spinnaker.kork.test.mimicker.producers.MonikerProducer;
import com.netflix.spinnaker.kork.test.mimicker.producers.NetworkProducer;
import com.netflix.spinnaker.kork.test.mimicker.producers.RandomProducer;
import com.netflix.spinnaker.kork.test.mimicker.producers.TextProducer;
import com.netflix.spinnaker.kork.test.mimicker.producers.aws.AwsProducer;
import java.security.SecureRandom;

/** A fake data generator for Spinnaker. */
public class Mimicker {

  private final SecureRandom secureRandom = new SecureRandom();
  private final DataContainer dataContainer;

  public Mimicker() {
    this(new DataContainer());
  }

  public Mimicker(DataContainer dataContainer) {
    this.dataContainer = dataContainer;
  }

  public TextProducer text() {
    return new TextProducer(dataContainer, random());
  }

  public RandomProducer random() {
    return new RandomProducer(secureRandom);
  }

  public NetworkProducer network() {
    return new NetworkProducer(random());
  }

  public MonikerProducer moniker() {
    return new MonikerProducer(random());
  }

  public AwsProducer aws() {
    return new AwsProducer(dataContainer, text(), random());
  }
}
