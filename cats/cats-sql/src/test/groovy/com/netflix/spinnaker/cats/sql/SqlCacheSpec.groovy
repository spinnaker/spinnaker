package com.netflix.spinnaker.cats.sql

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
    SqlTestUtil.cleanupDb(context)
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
    1 * ((SqlCache) cache).cacheMetrics.merge('test', 'foo', 1, 1, 0, 0, 1, 1, 0)

    when:
    ((SqlCache) cache).merge('foo', data)

    then:
    // SqlCacheMetrics currently sets items to # of items stored. The redis impl
    // sets this to # of items passed to merge, regardless of how many are actually stored
    // after deduplication. TODO: Having both metrics would be nice.
    1 * ((SqlCache) cache).cacheMetrics.merge('test', 'foo', 1, 0, 0, 0, 1, 0, 0)
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
    null                                                   || null                              || "1 = 1"
  }
}
