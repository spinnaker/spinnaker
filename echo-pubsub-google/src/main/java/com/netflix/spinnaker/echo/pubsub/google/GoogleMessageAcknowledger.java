/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.echo.pubsub.google;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.netflix.spinnaker.echo.pubsub.model.MessageAcknowledger;

public class GoogleMessageAcknowledger implements MessageAcknowledger {

  private AckReplyConsumer consumer;

  public GoogleMessageAcknowledger(AckReplyConsumer consumer) {
    this.consumer = consumer;
  }

  @Override
  public void ack() {
    consumer.ack();
  }

  @Override
  public void nack() {
    consumer.nack();
  }
}
