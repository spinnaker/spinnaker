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
import com.netflix.spinnaker.cats.sql.cache.SqlNamedCacheFactory
import com.netflix.spinnaker.config.SqlConstraintsInitializer
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.testcontainers.DockerClientFactory
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Unroll

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Requires({ DockerClientFactory.instance().isDockerAvailable() })
class SqlProviderCacheSpec extends ProviderCacheSpec {

  @Shared
  DSLContext context

  @AutoCleanup("close")
  HikariDataSource dataSource

  WriteableCache backingStore

  def cleanup() {
    SqlTestUtil.cleanupDb(context)
  }

  @Override
  SqlProviderCache getDefaultProviderCache() {
    getCache() as SqlProviderCache
  }

  @Override
  Cache getSubject() {
    def mapper = new ObjectMapper()
    def clock = Clock.fixed(Instant.EPOCH, ZoneId.of("UTC"))
    def sqlRetryProperties = new SqlRetryProperties(new RetryProperties(1, 10), new RetryProperties(1, 10))
    def sqlMetrics = new SpectatorSqlCacheMetrics(new NoopRegistry())
    def dynamicConfigService = Mock(DynamicConfigService) {
      getConfig(_ as Class, _ as String, _) >> 10
    }

    SqlTestUtil.TestDatabase testDatabase = SqlTestUtil.initTcMysqlDatabase()
    context = testDatabase.context
    dataSource = testDatabase.dataSource

    backingStore = new SqlCache(
      "test",
      context,
      mapper,
      null,
      clock,
      sqlRetryProperties,
      "test",
      sqlMetrics,
      dynamicConfigService,
      new SqlConstraintsInitializer().getDefaultSqlConstraints(SQLDialect.MYSQL),
      new SqlNamedCacheFactory.DefaultProviderCacheConfiguration()
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

  def 'save relationship to a non-existent object'() {
    // scenario:
    // Kubernetes Watcher receives a pod event that has a relationship to a replicaSet
    // ReplicaSet is not in the cache yet
    // We should be able to save the pod with the relationship to the replicaSet,
    // and the relationships from the replicaSet to the pod. But do not create the replicaSet object
    setup:
    def pod = createPod("78p4w")
    def replicaSet = createReplicaSet("54b6f5c894")

    when:
    putCacheResult([pod: [pod]]) // save object
    addCacheResult([pod: [withRelationships(pod, [replicaSet: [replicaSet.id]])]]) // save links

    then:
    // pod is cached with all attributes and relationships
    def cachedPods = cache.getAll("pod")
    cachedPods.size() == 1
    cachedPods[0].id == pod.id
    cachedPods[0].relationships == [replicaSet: [replicaSet.id]]
    cachedPods[0].attributes == pod.attributes

    // replicaSet object isn't cached
    def cachedReplicaSets = cache.getAll("replicaSet")
    cachedReplicaSets.size() == 0

    when:
    // and when we save the replicaSet object later
    putCacheResult([replicaSet: [replicaSet]])

    then:
    // replicaSet object is cached with the previously saved relationships
    def cachedReplicaSets2 = cache.getAll("replicaSet")
    cachedReplicaSets2.size() == 1
    cachedReplicaSets2[0].id == replicaSet.id
    cachedReplicaSets2[0].relationships == [
      pod: [pod.id]
    ]
    cachedReplicaSets2[0].attributes == replicaSet.attributes
  }

  def 'save relationship to an existent object'() {
    // scenario:
    // Kubernetes Watcher receives a pod event that has a relationship to a replicaSet
    // ReplicaSet is in the cache already
    // We should be able to save the pod with the relationship to the replicaSet,
    // and the relationships from the replicaSet to the pod. The replicaSet object shouldn't be updated.
    setup:
    // replicaSet is already in the cache
    def replicaSet = createReplicaSet("54b6f5c894")
    putCacheResult([replicaSet: [replicaSet]])

    when:
    // receive pod event
    def pod = createPod("78p4w")
    putCacheResult([pod: [pod]])
    addCacheResult([pod: [withRelationships(pod, [replicaSet: [replicaSet.id]])]])

    then:
    // pod is cached with all attributes and relationships
    def cachedPods = cache.getAll("pod")
    cachedPods.size() == 1
    cachedPods[0].id == pod.id
    cachedPods[0].relationships == [
      replicaSet: [replicaSet.id]
    ]
    cachedPods[0].attributes == pod.attributes

    // replicaSet relationships are updated, but the object is not
    def cachedReplicaSets = cache.getAll("replicaSet")
    cachedReplicaSets.size() == 1
    cachedReplicaSets[0].id == replicaSet.id
    cachedReplicaSets[0].relationships == [
      pod: [pod.id]
    ]
    cachedReplicaSets[0].attributes == replicaSet.attributes
  }

  def 'save second relationship: forward relationship'() {
    setup:
    def replicaSet = createReplicaSet("54b6f5c894")
    def pod = createPod("78p4w")
    def deployment = createDeployment("1")
    putCacheResult([
      replicaSet: [replicaSet],
      pod       : [pod],
      deployment: [deployment]
    ])

    // replicaSet is linked with pod
    addCacheResult([
      pod: [withRelationships(pod, [replicaSet: [replicaSet.id]])],
    ])

    when:
    // add a new forward link from the replicaSet to the deployment
    addCacheResult([
      replicaSet: [withRelationships(replicaSet, [deployment: [deployment.id]])],
    ])

    then:
    // replicaSet has two links
    def cachedReplicaSets = cache.getAll("replicaSet")
    cachedReplicaSets.size() == 1
    cachedReplicaSets[0].relationships == [
      pod: [pod.id],
      deployment: [deployment.id]
    ]
  }

  def 'save second relationship: backward relationship'() {
    setup:
    def replicaSet = createReplicaSet("54b6f5c894")
    def pod = createPod("78p4w")
    def deployment = createDeployment("1")
    putCacheResult([
      replicaSet: [replicaSet],
      pod       : [pod],
      deployment: [deployment]
    ])

    // replicaSet is linked with deployment
    addCacheResult([
      replicaSet: [withRelationships(replicaSet, [deployment: [deployment.id]])],
    ])

    when:
    // add a new backward link from the pod to the replicaSet
    addCacheResult([
      pod: [withRelationships(pod, [replicaSet: [replicaSet.id]])],
    ])

    then:
    // replicaSet has two links
    def cachedReplicaSets = cache.getAll("replicaSet")
    cachedReplicaSets.size() == 1
    cachedReplicaSets[0].relationships == [
      pod: [pod.id],
      deployment: [deployment.id]
    ]
  }

  def 'save multiple relationships in one batch'() {
    setup:
    def replicaSet = createReplicaSet("54b6f5c894")
    def pod1 = createPod("78p4w")
    def pod2 = createPod("78p4e")
    def deployment = createDeployment("1")
    putCacheResult([
      replicaSet: [replicaSet],
      pod       : [pod1, pod2],
      deployment: [deployment]
    ])

    when:
    // save multiple relationships in one batch
    addCacheResult([
      pod: [withRelationships(pod1, [replicaSet: [replicaSet.id]]), withRelationships(pod2, [replicaSet: [replicaSet.id]])],
      replicaSet: [withRelationships(replicaSet, [deployment: [deployment.id]])],
    ])

    then:
    // replicaSet has two links
    def cachedReplicaSets = cache.getAll("replicaSet")
    cachedReplicaSets.size() == 1
    cachedReplicaSets[0].relationships.size() == 2
    cachedReplicaSets[0].relationships.pod.toSet() == [pod1.id, pod2.id].toSet()
    cachedReplicaSets[0].relationships.deployment.toSet() == [deployment.id].toSet()
  }

  def 'evict object with relationships'() {
    // scenario:
    // Kubernetes Watcher receives a Pod Delete event. ReplicaSet object exists, and they both
    // have relationships to each other.
    // We should be able to evict the pod object, and the relationships from the pod to the replicaSet
    setup:
    def replicaSet = createReplicaSet("54b6f5c894")
    def pod = createPod("78p4w")
    putCacheResult([
      pod: [pod],
      replicaSet: [replicaSet]
    ])
    addCacheResult([
      pod: [withRelationships(pod, [replicaSet: [replicaSet.id]])],
      replicaSet: [withRelationships(replicaSet, [pod: [pod.id]])]
    ])

    when:
    // evict pod
    getDefaultProviderCache().evictDeletedItems("pod", [pod.id])

    then:
    // pod object is evicted
    def cachedPods = cache.getAll("pod")
    cachedPods.size() == 0

    // replicaSet relationships are updated
    def cachedReplicaSets = cache.getAll("replicaSet")
    cachedReplicaSets.size() == 1
    cachedReplicaSets[0].id == replicaSet.id
    cachedReplicaSets[0].relationships == [:]
    cachedReplicaSets[0].attributes == replicaSet.attributes
  }

  def 'eviction deletes only affected relationships'() {
    // 2 replicaSets with 2 corresponding pods. Delete one pod.
    // The other pod, both replicaSet, and the other relationship should remain.
    setup:
    def replicaSet1 = createReplicaSet("54b6f5c894")
    def pod1 = createPod("78p4w")

    def replicaSet2 = createReplicaSet("1111111111")
    def pod2 = createPod("1111111111")

    putCacheResult([
      pod: [pod1, pod2],
      replicaSet: [replicaSet1, replicaSet2]
    ])
    addCacheResult([
      pod: [
        withRelationships(pod1, [replicaSet: [replicaSet1.id]]),
        withRelationships(pod2, [replicaSet: [replicaSet2.id]])
      ],
    ])

    when:
    // evict pod
    getDefaultProviderCache().evictDeletedItems("pod", [pod1.id])

    then:
    // pod object is evicted
    def cachedPods = cache.getAll("pod")
    cachedPods.size() == 1
    cachedPods[0].id == pod2.id
    cachedPods[0].relationships == [replicaSet: [replicaSet2.id]]
    cachedPods[0].attributes == pod2.attributes

    // replicaSet relationships are updated
    def cachedReplicaSets = cache.getAll("replicaSet")
    cachedReplicaSets.size() == 2
    def rs1 = cachedReplicaSets.find { it.id == replicaSet1.id }
    def rs2 = cachedReplicaSets.find { it.id == replicaSet2.id }
    rs1.relationships == [:]
    rs1.attributes == replicaSet1.attributes
    rs2.relationships == [pod: [pod2.id]]
    rs2.attributes == replicaSet2.attributes
  }

  def 'evict object without relationships'() {
    setup:
    def pod = createPod("78p4w")

    putCacheResult([
      pod: [pod],
    ])

    when:
    // evict pod
    getDefaultProviderCache().evictDeletedItems("pod", [pod.id])

    then:
    // pod object is evicted
    def cachedPods = cache.getAll("pod")
    cachedPods.size() == 0
  }

  def 'create a new application and a cluster. logical types'() {
    setup:
    def deployment = createDeployment("1")
    def application = createData("kubernetes.v2:logical:applications:test-app", [name: 'test-app'])
    def cluster = createData("kubernetes.v2:logical:clusters:k8s-cluster:test-app:test-cluster", [name: 'test-cluster'])

    when:
    putCacheResult([
      applications: [application],
      clusters     : [cluster],
      deployment  : [deployment]
    ])
    addCacheResult([
      deployment: [withRelationships(deployment, [clusters: [cluster.id], applications: [application.id]])],
      clusters: [withRelationships(cluster, [applications: [application.id]])],
    ])

    then:
    def app = cache.getAll("applications")
    app.size() == 1
    app[0].id == application.id
    app[0].attributes == application.attributes
    app[0].relationships == [
      clusters: [cluster.id],
      deployment: [deployment.id]
    ]

    def clusters = cache.getAll("clusters")
    clusters.size() == 1
    clusters[0].id == cluster.id
    clusters[0].attributes == cluster.attributes
    clusters[0].relationships == [
      applications: [application.id],
      deployment: [deployment.id]
    ]

    def deployments = cache.getAll("deployment")
    deployments.size() == 1
    deployments[0].id == deployment.id
    deployments[0].attributes == deployment.attributes
    deployments[0].relationships == [
      clusters: [cluster.id],
      applications: [application.id]
    ]
  }

  def 'should save bidirectional relationships without duplicates'() {
    setup:
    def replicaSet = createReplicaSet("54b6f5c894")
    def pod = createPod("78p4w")

    when:
    putCacheResult([
      pod: [pod],
      replicaSet: [replicaSet],
    ])
    // relationships are saved in both directions
    addCacheResult([
      pod: [withRelationships(pod, [replicaSet: [replicaSet.id]])],
      replicaSet: [withRelationships(replicaSet, [pod: [pod.id]])]
    ])

    then:
    // each object has exact one link with correct fields
    context.fetch("select * from cats_v1_test_replicaSet_rel").map {
      [
        id       : it["id"],
        rel_id   : it["rel_id"],
        rel_type : it["rel_type"],
        rel_agent : it["rel_agent"],
      ]
    } == [
      [
        id       : replicaSet.id,
        rel_id   : pod.id,
        rel_type : "pod",
        rel_agent : "KubernetesStreamingCachingAgent"
      ]
    ]

    context.fetch("select * from cats_v1_test_pod_rel").map {
      [
        id       : it["id"],
        rel_id   : it["rel_id"],
        rel_type : it["rel_type"],
        rel_agent : it["rel_agent"],
      ]
    } == [
      [
        id       : pod.id,
        rel_id   : replicaSet.id,
        rel_type : "replicaSet",
        rel_agent : "KubernetesStreamingCachingAgent"
      ]
    ]
  }

  CacheData createDeployment(String id) {
    def fullId = "kubernetes.v2:infrastructure:deployment:k8s-cluster:test-namespace:test-app-" + id
    def attributes = [
      apiVersion: "v1",
      kind      : "Deployment",
      "manifest": [template: [spec: [contianers: [[image: "image -> my-app/my-app:1.2.3"]]]]],
    ]
    return createData(fullId, attributes, [:])
  }

  CacheData createReplicaSet(String id) {
    def fullId = "kubernetes.v2:infrastructure:replicaSet:k8s-cluster:test-namespace:test-app-" + id
    def attributes = [
      apiVersion: "v1",
      kind      : "ReplicaSet",
      "manifest": [template: [spec: [contianers: [[image: "image -> my-app/my-app:1.2.3"]]]]],
    ]
    return createData(fullId, attributes, [:])
  }

  CacheData createPod(String id) {
    def fullId = "kubernetes.v2:infrastructure:pod:k8s-cluster:test-namespace:test-app-" + id
    def attributes = [
      apiVersion: "v1",
      kind: "Pod",
      "manifest": [spec: [contianers: [[image: "image -> my-app/my-app:1.2.3"]]]],
    ]
    return createData(fullId, attributes, [:])
  }

  CacheData withRelationships(CacheData data, Map<String, List<String>> relationships) {
    return createData(data.id, data.attributes, relationships)
  }

  void putCacheResult(Map<String, List<CacheData>> cacheResult) {
    getDefaultProviderCache().putCacheResult("KubernetesStreamingCachingAgent", [], new DefaultCacheResult(cacheResult))
  }

  void addCacheResult(Map<String, List<CacheData>> cacheResult) {
    getDefaultProviderCache().addCacheResult("KubernetesStreamingCachingAgent", [], new DefaultCacheResult(cacheResult))
  }

  void addInformative(String type, String id, CacheData cacheData = createData(id)) {
    defaultProviderCache.putCacheResult('testAgent', ['informative'], new DefaultCacheResult((type): [cacheData]))
  }

}
