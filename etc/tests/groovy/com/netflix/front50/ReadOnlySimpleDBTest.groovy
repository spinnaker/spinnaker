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

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.simpledb.AmazonSimpleDB
import com.amazonaws.services.simpledb.AmazonSimpleDBClient
import com.amazonaws.services.simpledb.model.SelectRequest
import spock.lang.Specification

/**
 * Created by aglover on 4/22/14.
 */
class ReadOnlySimpleDBTest extends Specification {
  static AmazonSimpleDB client
  static String domain = "RESOURCE_REGISTRY"

  def setupSpec() {
    client = new AmazonSimpleDBClient(new BasicAWSCredentials(
      System.properties["aws.key"], System.properties["aws.secret"]
    ))
  }

  void 'should list items in a table via name'() {
    String qry = "select * from `" + domain + "` where itemName()='SAMPLEAPP'";
    SelectRequest selectRequest = new SelectRequest(qry);

    def itms = client.select(selectRequest).getItems()
    def item = itms[0]
    expect:
    itms != null
    itms.size() == 1
    item.name == 'SAMPLEAPP'
  }

  void 'should list items in a table'() {
    String qry = "select * from `" + domain + "` limit 2500";
    SelectRequest selectRequest = new SelectRequest(qry);

    def itms = client.select(selectRequest).getItems()

    expect:
    itms != null
    itms.size() >= 1416
  }

}
