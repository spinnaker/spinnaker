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

import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import org.springframework.security.access.AccessDeniedException
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.regex.Pattern;

import static com.netflix.spinnaker.clouddriver.model.DataProvider.IdentifierType.*
import static com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonS3StaticDataProviderConfiguration.StaticRecordType.*

class AmazonS3DataProviderSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def amazonClientProvider = Mock(AmazonClientProvider)
  def accountCredentialsRepository = Mock(AccountCredentialsRepository)
  def configuration = new AmazonS3StaticDataProviderConfiguration([
    new AmazonS3StaticDataProviderConfiguration.StaticRecord("staticId", string, "accountName", "us-east-1", "bucket", "key"),
    new AmazonS3StaticDataProviderConfiguration.StaticRecord("staticListId", list, "accountName", "us-east-1", "bucket", "listKey")
  ], [
    new AmazonS3StaticDataProviderConfiguration.AdhocRecord(
      id: "adhocId",
      bucketNamePattern: Pattern.compile(".*restricted.*"),
      objectKeyPattern: Pattern.compile(".*magic.*")
    )
  ])

  def s3Object = Mock(S3Object)

  @Subject
  def dataProvider = Spy(AmazonS3DataProvider, constructorArgs: [
    objectMapper,
    amazonClientProvider,
    accountCredentialsRepository,
    configuration
  ])

  void setup() {
    accountCredentialsRepository.getAll() >> {
      [
        Mock(AccountCredentials) {
          getAccountId() >> "12345678910"
          getName() >> "accountName"
        }
      ]
    }
  }

  def "supports account lookup by id or name"() {
    expect:
    dataProvider.getAccountForIdentifier(Adhoc, "accountName:us-east-1:my_bucket") == "accountName"
    dataProvider.getAccountForIdentifier(Adhoc, "12345678910:us-east-1:my_bucket") == "accountName"
    dataProvider.getAccountForIdentifier(Static, "staticId") == "accountName"
  }

  @Unroll
  def "should raise exception if identifier is unsupported"() {
    expect:
    try {
      dataProvider.getAccountForIdentifier(type, id)
      assert !expectsException
    } catch (Exception ignored) {
      assert expectsException
    }

    where:
    type   | id                                       || expectsException
    Adhoc  | "invalidAccountName:us-east-1:my_bucket" || true
    Adhoc  | "accountName:us-east-1:my_bucket"        || false
    Static | "invalidId"                              || true
    Static | "staticId"                               || false
  }

  @Unroll
  def "should support identifier iff configuration exists"() {
    expect:
    dataProvider.supportsIdentifier(type, id) == supported

    where:
    type   | id         || supported
    Adhoc  | "adhocId"  || true
    Static | "staticId" || true
    Adhoc  | "randomId" || false
    Static | "randomId" || false
  }

  def "should stream adhoc results"() {
    given:
    def outputStream = new ByteArrayOutputStream()

    when:
    dataProvider.getAdhocData("adhocId", "accountName:us-east-1:my_restricted_bucket", "magic/my_object", outputStream)

    then:
    1 * dataProvider.fetchObject("accountName", "us-east-1", "my_restricted_bucket", "magic/my_object") >> {
      return s3Object
    }
    1 * s3Object.getObjectContent() >> {
      return new S3ObjectInputStream(new ByteArrayInputStream("my example output!".bytes), null)
    }
    new String(outputStream.toByteArray(), "UTF-8") == "my example output!"
  }

  def "should deny requests to fetch adhoc results from non-whitelisted buckets or keys"() {
    when: "the object key is not whitelisted"
    dataProvider.getAdhocData("adhocId", "accountName:us-east-1:my_restricted_bucket", "my_object", null)

    then:
    thrown(AccessDeniedException)

    when: "the bucket name is not whitelisted"
    dataProvider.getAdhocData("adhocId", "accountName:us-east-1:my_bucket", "magic/my_object", null)

    then:
    thrown(AccessDeniedException)
  }

  def "should cache static results"() {
    when:
    def result = dataProvider.getStaticData("staticId", [:])

    then:
    1 * dataProvider.fetchObject("accountName", "us-east-1", "bucket", "key") >> {
      return s3Object
    }
    1 * s3Object.getObjectContent() >> {
      return new S3ObjectInputStream(new ByteArrayInputStream("my example output!".bytes), null)
    }
    dataProvider.getStaticCacheStats().hitCount() == 0
    result == "my example output!"

    when:
    result = dataProvider.getStaticData("staticId", [:])

    then:
    dataProvider.getStaticCacheStats().hitCount() == 1
    result == "my example output!"
  }

  def "should support filters when static result is of type list"() {
    given:
    def resultsJson = objectMapper.writeValueAsString([
      [name: "foo", id: 1],
      [name: "bar", id: 2],
      [name: "baz", id: 3]
    ])

    when:
    def result = dataProvider.getStaticData("staticListId", [name: "bar"])

    then:
    1 * dataProvider.fetchObject("accountName", "us-east-1", "bucket", "listKey") >> {
      return s3Object
    }
    1 * s3Object.getObjectContent() >> {
      return new S3ObjectInputStream(
        new ByteArrayInputStream(resultsJson.bytes), null
      )
    }
    result == [
      [name: "bar", id: 2]
    ]

    when:
    result = dataProvider.getStaticData("staticId", [name: "bar"])

    then:
    1 * dataProvider.fetchObject("accountName", "us-east-1", "bucket", "key") >> {
      return s3Object
    }
    1 * s3Object.getObjectContent() >> {
      return new S3ObjectInputStream(
        new ByteArrayInputStream(resultsJson.bytes), null
      )
    }

    // if type != list than all results should be returned as-is w/o filtering
    result == resultsJson
  }
}
