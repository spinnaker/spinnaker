/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.provider.view

import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.transform.Canonical
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

import java.util.regex.Pattern;

@Component
@ConfigurationProperties('data.s3')
@Canonical
class AmazonS3StaticDataProviderConfiguration {
  enum StaticRecordType {
    list,
    object,
    string
  }

  @Canonical
  static class StaticRecord {
    String id
    StaticRecordType type
    String bucketAccount
    String bucketRegion
    String bucketName
    String bucketKey
  }

  @Canonical
  static class AdhocRecord {
    String id
    Pattern bucketNamePattern
    Pattern objectKeyPattern

    void setBucketNamePattern(String bucketNamePattern) {
      this.bucketNamePattern = Pattern.compile(bucketNamePattern)
    }

    void setObjectKeyPattern(String objectKeyPattern) {
      this.objectKeyPattern = Pattern.compile(objectKeyPattern)
    }
  }

  List<StaticRecord> staticRecords = []
  List<AdhocRecord> adhocRecords = []

  StaticRecord getStaticRecord(String id) {
    def staticRecord = getStaticRecords().find { it.getId().equalsIgnoreCase(id) }
    if (!staticRecord) {
      throw new NotFoundException("No static data found (id: ${id})")
    }

    return staticRecord
  }

  AdhocRecord getAdhocRecord(String id) {
    def adhocRecord = getAdhocRecords().find { it.getId().equalsIgnoreCase(id) }
    if (!adhocRecord) {
      throw new NotFoundException("No adhoc data found (id: ${id})")
    }

    return adhocRecord
  }


}
