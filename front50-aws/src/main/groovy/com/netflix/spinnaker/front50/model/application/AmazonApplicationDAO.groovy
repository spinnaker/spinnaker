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
import com.netflix.spinnaker.front50.exception.NotFoundException
import groovy.transform.Canonical

/**
 * Created by aglover on 4/22/14.
 */
@Canonical
class AmazonApplicationDAO implements ApplicationDAO {

  protected AmazonSimpleDB awsSimpleDBClient
  protected String domain

  @Override
  boolean isHealthly() {
    awsSimpleDBClient.select(new SelectRequest("select * from `${domain}` limit 1"))
    return true
  }

  @Override
  Set<Application> search(Map<String, String> attributes) {
    //def params = attributes.collect { k, v -> "$k = '$v'" }
    //def items = query "select * from `${domain}` where ${params.join(" and ")} limit 2500"
    def items = all().findAll { app ->
      def result = true
      attributes.each { k, v ->
        if (app.hasProperty(k) && (!app[k] || ((String)app[k]).toLowerCase() != v?.toLowerCase())) {
          result = false
        }
      }
      result
    } as Set
    if (!items) {
      throw new NotFoundException("No Application found for search criteria $attributes in domain ${domain}")
    }
    items
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
  Application findByName(String name) throws NotFoundException {
    def items = query "select * from `${domain}` where itemName()='${name}'"
    if (items.size() > 0) {
      return mapToApp(items[0])
    } else {
      throw new NotFoundException("No Application found by name of ${name} in domain ${domain}")
    }
  }

  @Override
  Set<Application> all() throws NotFoundException {
    def items = query "select * from `${domain}` limit 2500"
    if (items.size() > 0) {
      return items.collect { mapToApp(it) }
    } else {
      throw new NotFoundException("No Applications found in domain ${domain}")
    }
  }

  static Collection<ReplaceableAttribute> buildAttributes(def properties, boolean replace) {
    properties.collectMany { key, value -> [new ReplaceableAttribute(key, value, replace)] }
  }

  private static Application mapToApp(Item item) {
    Map<String, String> map = item.attributes.collectEntries { [it.name, (it.value ?: null)] }
    map['name'] = item.name
    return new Application(map)
  }

  private List<Item> query(String query) {
    List<Item> results = []
    String nextToken = null
    while (true) {
      def result = awsSimpleDBClient.select(new SelectRequest(query).withNextToken(nextToken))
      results += result.items
      nextToken = result.nextToken
      if (!nextToken) {
        return results
      }
    }
  }
}
