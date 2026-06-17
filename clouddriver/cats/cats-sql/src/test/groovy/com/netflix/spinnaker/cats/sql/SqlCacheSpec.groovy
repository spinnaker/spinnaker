package com.netflix.spinnaker.cats.sql

import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.cats.cache.WriteableCacheSpec
import com.netflix.spinnaker.cats.sql.cache.SqlCache
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import org.jooq.impl.DSL
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Unroll

abstract class SqlCacheSpec extends WriteableCacheSpec {

  @Shared
  DSLContext context

  @AutoCleanup("close")
  HikariDataSource dataSource

  def cleanup() {
    if (context != null) {
      SqlTestUtil.cleanupDb(context)
    }
  }

  def 'should handle invalid type'() {
    given:
    def data = createData('blerp', [a: 'b'])
    ((SqlCache) cache).merge('foo.bar', data)

    when:
    def retrieved = ((SqlCache) cache).getAll('foo.bar')

    then:
    retrieved.size() == 1
    retrieved.findAll { it.id == "blerp" }.size() == 1
  }

  def 'should not write an item if it is unchanged'() {
    setup:
    def data = createData('blerp', [a: 'b'])

    when:
    ((SqlCache) cache).merge('foo', data)

    then:
    1 * ((SqlCache) cache).cacheMetrics.merge('test', 'foo', 1, 1, 0, 0, 1, 1, 0, 0)

    when:
    ((SqlCache) cache).merge('foo', data)

    then:
    // SqlCacheMetrics currently sets items to # of items stored. The redis impl
    // sets this to # of items passed to merge, regardless of how many are actually stored
    // after deduplication. TODO: Having both metrics would be nice.
    1 * ((SqlCache) cache).cacheMetrics.merge('test', 'foo', 1, 0, 0, 0, 1, 0, 0, 0)
  }

  def 'mergeAll with two items that have the same id preserves the existing item'() {
      given: 'one item in the cache'
      String id = 'bar'
      def itemOneAttributes = [att1: 'val1']
      CacheData itemOne = createData(id, itemOneAttributes)
      def itemTwoAttributes = [att2: 'val2']
      CacheData itemTwo = createData(id, itemTwoAttributes)
      String type = 'foo'
      cache.mergeAll(type, [ itemOne ])
      assert itemOneAttributes.equals(cache.get(type, id).attributes)

      when: 'adding both items'
      cache.mergeAll(type, [ itemOne, itemTwo ])

      then: 'itemOne is in the cache'
      itemOneAttributes.equals(cache.get(type, id).attributes)

      and: 'the metrics report a duplicate'
      1 * ((SqlCache) cache).cacheMetrics.merge('test', type, 2, 0, 0, 0, 1, 0, 0, 1)

      when: 'storing the items again'
      cache.mergeAll(type, [ itemOne, itemTwo ])

      then: 'itemOne is in the cache'
      itemOneAttributes.equals(cache.get(type, id).attributes)

      and: 'the metrics report a duplicate'
      1 * ((SqlCache) cache).cacheMetrics.merge('test', type, 2, 0, 0, 0, 1, 0, 0, 1)
  }

  def 'all items are stored and retrieved when larger than sql chunk sizes'() {
    given:
    def data = (1..10).collect { createData("fnord-$it") }
    ((SqlCache) cache).mergeAll('foo', data)

    when:
    def retrieved = ((SqlCache) cache).getAll('foo')

    then:
    retrieved.size() == 10
    retrieved.findAll { it.id == "fnord-5" }.size() == 1
  }

  @Unroll
  def 'generates where clause based on cacheFilters'() {
    when:
    def relPrefixes = ((SqlCache) cache).getRelationshipFilterPrefixes(filter)
    def where = ((SqlCache) cache).getRelWhere(relPrefixes, queryPrefix)

    then:
    where.toString() == expected

    where:
    filter                                                 || queryPrefix                       || expected
    RelationshipCacheFilter.none()                         || DSL.field("meowdy").eq("partner") || "meowdy = 'partner'"
    null                                                   || DSL.field("meowdy").eq("partner") || "meowdy = 'partner'"
    RelationshipCacheFilter.include("instances", "images") || null                              || "(\n  rel_type like 'instances%'\n  or rel_type like 'images%'\n)"
    RelationshipCacheFilter.include("images")              || DSL.field("meowdy").eq("partner") || "(\n  meowdy = 'partner'\n  and rel_type like 'images%'\n)"
    null                                                   || null                              || "true"
  }
}
