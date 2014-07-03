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

package com.netflix.spinnaker.front50.model.application

import com.amazonaws.services.simpledb.AmazonSimpleDB
import com.amazonaws.services.simpledb.model.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Created by aglover on 4/22/14.
 */
@Component("SimpleDB")
class DefaultApplicationDAO implements ApplicationDAO {

  @Autowired
  AmazonSimpleDB awsSimpleDBClient

  @Value('${application.simpledb.domain:RESOURCE_REGISTRY}')
  String domain

  @Override
  boolean isHealthly() {
    this.awsSimpleDBClient == null || listDomains().size() <= 0
  }

  private List<String> listDomains() {
    awsSimpleDBClient.listDomains(new ListDomainsRequest().withMaxNumberOfDomains(1)).getDomainNames()
  }

  @Override
  Application create(String id, Map<String, String> properties) {
    properties['createTs'] = System.currentTimeMillis() as String
    awsSimpleDBClient.putAttributes(new PutAttributesRequest().withDomainName(domain).
      withItemName(id).withAttributes(buildAttributes(properties, false)))
    Application application = new Application(properties)
    application.name = id
    return application
  }

  @Override
  void delete(String id) {
    awsSimpleDBClient.deleteAttributes(
      new DeleteAttributesRequest().withDomainName(domain).withItemName(id))
  }

  @Override
  void update(String id, Map<String, String> properties) {
    properties['updateTs'] = System.currentTimeMillis() as String
    awsSimpleDBClient.putAttributes(new PutAttributesRequest().withDomainName(domain).
      withItemName(id).withAttributes(buildAttributes(properties, true)))
  }

  @Override
  Application findByName(String name) {
    def items = query "select * from `${domain}` where itemName()='${name}'"
    if (items.size() > 0) {
      return mapToApp(items[0])
    } else {
      throw new RuntimeException("No Application found by name of ${name} in domain ${domain}")
    }
  }

  @Override
  Set<Application> all() {
    def items = query "select * from `${domain}` limit 2500"
    if (items.size() > 0) {
      return items.collect { mapToApp(it) }
    } else {
      throw new RuntimeException("No Applications found in domain ${domain}")
    }
  }

  static Collection<ReplaceableAttribute> buildAttributes(def properties, boolean replace) {
    properties.collectMany { key, value -> [new ReplaceableAttribute(key, value, replace)] }
  }

  private static Application mapToApp(Item item) {
    Map<String, String> map = item.attributes.collectEntries { [it.name, it.value] }
    map['name'] = item.name
    return new Application(map)
  }

  private List<Item> query(String query) {
    awsSimpleDBClient.select(new SelectRequest(query)).getItems()
  }
}
