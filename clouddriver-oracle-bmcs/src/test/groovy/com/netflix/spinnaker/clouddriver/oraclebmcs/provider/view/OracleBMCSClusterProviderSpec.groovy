/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSCloudProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSInstance
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification

class OracleBMCSClusterProviderSpec extends Specification {

  def "get a server group from the cache"() {
    setup:
    def cache = Mock(Cache)
    def ap = Mock(AccountCredentialsProvider)
    ap.getCredentials(_) >> null
    def clusterProvider = new OracleBMCSClusterProvider(null, new ObjectMapper(), ap, cache)
    def identifiers = Mock(Collection)
    def attributes = ["name": "foo-v001", "targetSize": 5]
    def mockData = Mock(CacheData)
    Collection<CacheData> cacheData = [mockData]
    def id = "${OracleBMCSCloudProvider.ID}:${Keys.Namespace.SERVER_GROUPS}:foo-test:account1:us-phoenix-1:foo-test-v001"

    when:
    def serverGroup = clusterProvider.getServerGroup("account1", "us-phoenix-1", "foo-test-v001")

    then:
    1 * cache.filterIdentifiers(Keys.Namespace.SERVER_GROUPS.ns, id) >> identifiers
    1 * cache.getAll(Keys.Namespace.SERVER_GROUPS.ns, identifiers, _) >> cacheData
    1 * mockData.attributes >> attributes
    1 * mockData.id >> id
    serverGroup.name == attributes["name"]
    serverGroup.capacity.desired == attributes["targetSize"]

    noExceptionThrown()
  }

  def "get a cluster from the cache"() {
    setup:
    def cache = Mock(Cache)
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.name >> "account1"
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    accountCredentialsProvider.getCredentials(_) >> creds
    def instanceProvider = Mock(OracleBMCSInstanceProvider)
    instanceProvider.getInstance(_, _, _) >> new OracleBMCSInstance()
    def clusterProvider = new OracleBMCSClusterProvider(instanceProvider, new ObjectMapper(), accountCredentialsProvider, cache)
    def attributes = ["name": "foo-test-v001", "targetSize": 5, "instances": [["name": "blah"]]]
    def mockData = Mock(CacheData)
    Collection<CacheData> cacheData = [mockData]
    def id = "${OracleBMCSCloudProvider.ID}:${Keys.Namespace.SERVER_GROUPS}:foo-test:account1:us-phoenix-1:foo-test-v001"

    when:
    def cluster = clusterProvider.getCluster("foo", "account1", "foo-test")

    then:
    1 * cache.getIdentifiers(Keys.Namespace.SERVER_GROUPS.ns) >> [id]
    1 * cache.getAll(Keys.Namespace.SERVER_GROUPS.ns, [id], _) >> cacheData
    1 * mockData.attributes >> attributes
    mockData.id >> id
    cluster.name == "foo-test"
    cluster.serverGroups.size() == 1
    cluster.serverGroups.first().name == attributes["name"]
    cluster.serverGroups.first().capacity.desired == attributes["targetSize"]

    noExceptionThrown()
  }

}
