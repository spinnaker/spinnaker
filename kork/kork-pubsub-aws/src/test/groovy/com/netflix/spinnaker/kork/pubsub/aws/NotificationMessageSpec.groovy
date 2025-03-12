/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.kork.pubsub.aws

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification


class NotificationMessageSpec extends Specification {
  def 'notification message serde'() {
    given:
    String payload = '''
      {\"Records\":[
      \"eventVersion\":\"2.0\",
      \"eventSource\":\"aws:s3\",
      \"awsRegion\":\"us-west-2\","
      \"eventName\":\"ObjectCreated:Put\","
      \"s3\":{"
      \"s3SchemaVersion\":\"1.0\","
      \"configurationId\":\"prestaging_front50_events\","
      \"bucket\":{\"name\":\"us-west-2.spinnaker-prod\",\"ownerIdentity\":{\"principalId\":\"A2TW6LBRCW9VEM\"},\"arn\":\"arn:aws:s3:::us-west-2.spinnaker-prod\"},"
      \"object\":{\"key\":\"prestaging/front50/pipelines/31ef9c67-1d67-474f-a653-ac4b94c90817/pipeline-metadata.json\",\"versionId\":\"8eyu4_RfV8EUqTnClhkKfLK5V4El_mIW\"}}"
      "}]}
      '''

    NotificationMessage notificationMessage = new NotificationMessage(
      "Notification",
      "4444-ffff",
      "arn:aws:sns:us-west-2:100:topicName",
      "Amazon S3 Notification",
      payload,
      [:]
    )
    def objectMapper = new ObjectMapper()
    String snsMessage = objectMapper.writeValueAsString(notificationMessage)

    when:
    String result = objectMapper.readValue(snsMessage, NotificationMessage).message

    then:
    result == payload
  }
}
