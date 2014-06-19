/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.front50

import co.freeside.betamax.Betamax
import co.freeside.betamax.Recorder
import co.freeside.betamax.httpclient.BetamaxHttpsSupport
import co.freeside.betamax.httpclient.BetamaxRoutePlanner
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.simpledb.AmazonSimpleDBClient
import com.amazonaws.services.simpledb.model.*
import com.jayway.awaitility.groovy.AwaitilitySupport
import org.junit.Rule
import spock.lang.Specification

import static java.util.concurrent.TimeUnit.SECONDS

/**
 * Created by aglover on 4/24/14.
 */
@Mixin(AwaitilitySupport)
class CreateUpdateSimpleDBTest extends Specification {
  static AmazonSimpleDBClient client
  final static String DOMAIN = "TEST_RESOURCE_REGISTRY_ONLY"

  @Rule
  Recorder recorder = new Recorder()

  @Betamax(tape = 'aws-simpledb-1')
  void setupSpec() {
    client = new AmazonSimpleDBClient(new BasicAWSCredentials(
      System.properties["aws.key"], System.properties["aws.secret"]
    ), new ClientConfiguration().withProxyHost('127.0.0.1').withProxyPort(5555))

    BetamaxRoutePlanner.configure(client.client.httpClient)
    BetamaxHttpsSupport.configure(client.client.httpClient)

    client.createDomain(new CreateDomainRequest(DOMAIN))
  }

  @Betamax(tape = 'aws-simpledb-1')
  def cleanupSpec() {
    client.deleteDomain(new DeleteDomainRequest(DOMAIN))
  }

  /**
   * puts 1 row into database before each test
   */
  @Betamax(tape = 'aws-simpledb-1')
  void setup() {
    def input = [
      "group"      : "tst-group",
      "type"       : "test type",
      "description": "test",
      "owner"      : "Kevin McEntee",
      "email"      : "web@netflix.com",
      "updatedTs"  : "1265752693581",
      "tags"       : "[1,ok, test]"]

    Collection<ReplaceableAttribute> attributes = []
    input.each { key, value ->
      attributes << new ReplaceableAttribute(key, value, false)
    }
    client.putAttributes(new PutAttributesRequest().withDomainName(DOMAIN).
      withItemName("TEST_APP").withAttributes(attributes))
  }

  @Betamax(tape = 'aws-simpledb-1')
  void 'updates should work by setting true to replaceable attribute'() {
    Collection<ReplaceableAttribute> attributes = []
    attributes << new ReplaceableAttribute('owner', 'aglover@netflix.com', true)
    when:
    client.putAttributes(new PutAttributesRequest().withDomainName(DOMAIN).
      withItemName("TEST_APP").withAttributes(attributes))

    then:
    notThrown(Exception)
    await().atMost(5, SECONDS).until {
      def itms = client.select(new SelectRequest("select * from `${DOMAIN}` where itemName()='TEST_APP'")).getItems()
      Item item = itms[0]
      itms != null
      def attr = item.attributes.find { it.name == 'owner' }
      attr.value == 'aglover@netflix.com'
    }
  }

  @Betamax(tape = 'aws-simpledb-1')
  void 'create should result in a new row'() {
    def testData = [
      ["name": "SAMPLEAPP", "attrs":
        ["group"      : "tst-group", "type": "test type",
         "description": "test", "owner": "Kevin McEntee",
         "email"      : "web@netflix.com",
         "updatedTs"  : "1265752693581", "tags": "[1,ok, test]"]
      ],
      ["name": "Asgard", attrs:
        ["group"      : "tst-group-2", "type": "test type",
         "description": "test", "owner": "Andy McEntee",
         "email"      : "web@netflix.com", "monitorBucketType": "blah",
         "updatedTs"  : "1265752693581"]
      ]
    ]

    when:
    testData.each { imap ->
      Collection<ReplaceableAttribute> attributes = []
      imap["attrs"].each { key, value ->
        attributes << new ReplaceableAttribute(key, value, false)
      }
      client.putAttributes(new PutAttributesRequest().withDomainName(DOMAIN).
        withItemName(imap["name"]).withAttributes(attributes))
    }

    then:
    notThrown(Exception)
  }

  @Betamax(tape = 'aws-simpledb-1')
  void 'create throw an error if an attribute is null and not result in a new row'() {
    def testData = [
      ["name": "SAMPLEAPP", "attrs":
        ["group"      : "tst-group",
         "type"       : "test type",
         "description": "test",
         "owner"      : "Kevin McEntee",
         "email"      : "web@netflix.com",
         "updatedTs"  : "1265752693581",
         "tags"       : null]
      ]
    ]

    when:
    testData.each { imap ->
      Collection<ReplaceableAttribute> attributes = []
      imap["attrs"].each { key, value ->
        attributes << new ReplaceableAttribute(key, value, false)
      }
      client.putAttributes(new PutAttributesRequest().withDomainName(DOMAIN).
        withItemName(imap["name"]).withAttributes(attributes))
    }

    then:
    thrown(Exception)
  }
}
