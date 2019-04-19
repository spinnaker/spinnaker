package com.netflix.spinnaker.cats.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.provider.ProviderCacheSpec
import com.netflix.spinnaker.cats.sql.cache.SpectatorSqlCacheMetrics
import com.netflix.spinnaker.cats.sql.cache.SqlCache
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Unroll

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

import static com.netflix.spinnaker.kork.sql.test.SqlTestUtil.initDatabase

class SqlProviderCacheSpec extends ProviderCacheSpec {

  @Shared
  @AutoCleanup("close")
  SqlTestUtil.TestDatabase currentDatabase

  WriteableCache backingStore

  def setup() {
    (backingStore as SqlCache).clearCreatedTables()
    return initDatabase("jdbc:h2:mem:test")
  }


  def cleanup() {
    currentDatabase.context.dropSchemaIfExists("test")
  }

  @Override
  SqlProviderCache getDefaultProviderCache() {
    getCache() as SqlProviderCache
  }

  @Override
  Cache getSubject() {
    def mapper = new ObjectMapper()
    def clock = new Clock.FixedClock(Instant.EPOCH, ZoneId.of("UTC"))
    def sqlRetryProperties = new SqlRetryProperties()
    def sqlMetrics = new SpectatorSqlCacheMetrics(new NoopRegistry())
    def dynamicConfigService = Mock(DynamicConfigService) {
      getConfig(_, _, _) >> 10
    }
    currentDatabase = initDatabase()
    backingStore = new SqlCache(
      "test",
      currentDatabase.context,
      mapper,
      null,
      clock,
      sqlRetryProperties,
      "test",
      sqlMetrics,
      dynamicConfigService
    )

    return new SqlProviderCache(backingStore)
  }

  @Unroll
  def 'informative relationship filtering behaviour'() {
    setup:
    populateOne(
      'serverGroup',
      'foo',
      createData('foo', [canhaz: "attributes"], [rel1: ["rel1"]])
    )

    addInformative(
      'loadBalancer',
      'bar',
      createData('bar', [canhaz: "attributes"], [serverGroup: ["foo"]])
    )

    addInformative(
      'instances',
      'baz',
      createData('baz', [canhaz: "attributes"], [serverGroup: ["foo"]])
    )

    expect:
    cache.get('serverGroup', 'foo').relationships.keySet() == ["instances", "loadBalancer", "rel1"] as Set
    cache.get('serverGroup', 'foo', filter).relationships.keySet() == expectedRelationships as Set

    cache.getAll('serverGroup').iterator().next().relationships.keySet() == ["instances", "loadBalancer", "rel1"] as Set
    cache.getAll('serverGroup', filter).iterator().next().relationships.keySet() == expectedRelationships as Set

    where:
    filter                                                       || expectedRelationships
    RelationshipCacheFilter.include("loadBalancer")              || ["loadBalancer"]
    RelationshipCacheFilter.include("instances", "loadBalancer") || ["instances", "loadBalancer"]
    RelationshipCacheFilter.include("rel3")                      || []
    RelationshipCacheFilter.none()                               || []
  }

  def 'can index and retrieve by application'() {
    setup:
    def sgIdsForAppFoo = 'fooSg1'..'fooSg9'
    def sgIdsForAppBar = 'barSg1'..'barSg9'

    sgIdsForAppFoo.each {
      populateOne('serverGroup', it, createData(it, [application: "foo"], [:]))
    }

    sgIdsForAppBar.each {
      populateOne('serverGroup', it, createData(it, [application: "bar"], [:]))
    }

    when:
    def fooData = cache.getAllByApplication("serverGroup", "foo", RelationshipCacheFilter.none())
    def barData = cache.getAllByApplication("serverGroup", "bar", RelationshipCacheFilter.none())

    then:
    fooData["serverGroup"].findAll { it.attributes.application != "foo" } == []
    barData["serverGroup"].findAll { it.attributes.application != "bar" } == []
    fooData["serverGroup"].collect { it.id }.sort() == sgIdsForAppFoo
    barData["serverGroup"].collect { it.id }.sort() == sgIdsForAppBar
  }

  def 'can retrieve multiple types by application'() {
    setup:
    def sgIdsForAppFoo = 'fooSg1'..'fooSg3'
    def sgIdsForAppBar = 'barSg1'..'barSg3'
    def instanceIdsForAppFoo = 'fooInst1'..'fooInst3'
    def instanceIdsForAppBar = 'barInst1'..'barInst3'
    def filters = [serverGroup: RelationshipCacheFilter.none(), instances: RelationshipCacheFilter.none()]

    sgIdsForAppFoo.each {
      populateOne('serverGroup', it, createData(it, [application: "foo"], [:]))
    }

    sgIdsForAppBar.each {
      populateOne('serverGroup', it, createData(it, [application: "bar"], [:]))
    }

    instanceIdsForAppFoo.each {
      populateOne('instances', it, createData(it, [application: "foo"], [:]))
    }

    instanceIdsForAppBar.each {
      populateOne('instances', it, createData(it, [application: "bar"], [:]))
    }

    when:
    def fooData = cache.getAllByApplication(["instances", "serverGroup"], "foo", filters)

    then:
    fooData["instances"].collect { it.id }.sort() == instanceIdsForAppFoo
    fooData["serverGroup"].collect { it.id }.sort() == sgIdsForAppFoo
  }

  void addInformative(String type, String id, CacheData cacheData = createData(id)) {
    defaultProviderCache.putCacheResult('testAgent', ['informative'], new DefaultCacheResult((type): [cacheData]))
  }

}
