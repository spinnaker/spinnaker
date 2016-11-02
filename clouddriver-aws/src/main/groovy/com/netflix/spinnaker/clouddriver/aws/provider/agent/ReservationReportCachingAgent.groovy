/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.ec2.model.DescribeAccountAttributesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonReservationReport.OverallReservationDetail
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.model.AmazonReservationReport
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import groovy.util.logging.Slf4j
import org.springframework.context.ApplicationContext
import rx.Observable
import rx.Scheduler

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.ToDoubleFunction

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.RESERVATION_REPORTS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.RESERVED_INSTANCES

@Slf4j
class ReservationReportCachingAgent implements CachingAgent, CustomScheduledAgent {
  private static final long DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1)
  private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5)

  final Collection<AgentDataType> types = Collections.unmodifiableCollection([
    AUTHORITATIVE.forType(RESERVATION_REPORTS.ns)
  ])

  private final Scheduler scheduler
  private final ApplicationContext ctx
  private Cache cacheView

  final AmazonClientProvider amazonClientProvider
  final Collection<NetflixAmazonCredentials> accounts
  final ObjectMapper objectMapper
  final AccountReservationDetailSerializer accountReservationDetailSerializer
  final Set<String> vpcOnlyAccounts
  final MetricsSupport metricsSupport

  ReservationReportCachingAgent(Registry registry,
                                AmazonClientProvider amazonClientProvider,
                                Collection<NetflixAmazonCredentials> accounts,
                                ObjectMapper objectMapper,
                                Scheduler scheduler,
                                ApplicationContext ctx) {
    this.amazonClientProvider = amazonClientProvider
    this.accounts = accounts

    def module = new SimpleModule()
    accountReservationDetailSerializer = new AccountReservationDetailSerializer()
    module.addSerializer(AmazonReservationReport.AccountReservationDetail.class, accountReservationDetailSerializer)

    this.objectMapper = objectMapper.copy().enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).registerModule(module)
    this.scheduler = scheduler
    this.ctx = ctx
    this.vpcOnlyAccounts = determineVpcOnlyAccounts()
    this.metricsSupport = new MetricsSupport(objectMapper, registry, { getCacheView() })
  }

  private Set<String> determineVpcOnlyAccounts() {
    def vpcOnlyAccounts = []

    accounts.each { credentials ->
      def amazonEC2 = amazonClientProvider.getAmazonEC2(credentials, credentials.regions[0].name)
      def describeAccountAttributesResult = amazonEC2.describeAccountAttributes(
        new DescribeAccountAttributesRequest().withAttributeNames("supported-platforms")
      )
      if (describeAccountAttributesResult.accountAttributes[0].attributeValues*.attributeValue == ["VPC"]) {
        vpcOnlyAccounts << credentials.name
      }
    }

    log.info("VPC Only Accounts: ${vpcOnlyAccounts.join(", ")}")
    return vpcOnlyAccounts
  }

  @Override
  long getPollIntervalMillis() {
    return DEFAULT_POLL_INTERVAL_MILLIS
  }

  @Override
  long getTimeoutMillis() {
    return DEFAULT_TIMEOUT_MILLIS
  }

  static class MutableCacheData implements CacheData {
    final String id
    int ttlSeconds = -1
    final Map<String, Object> attributes = [:]
    final Map<String, Collection<String>> relationships = [:].withDefault { [] as Set }

    public MutableCacheData(String id) {
      this.id = id
    }

    @JsonCreator
    public MutableCacheData(@JsonProperty("id") String id,
                            @JsonProperty("attributes") Map<String, Object> attributes,
                            @JsonProperty("relationships") Map<String, Collection<String>> relationships) {
      this(id);
      this.attributes.putAll(attributes);
      this.relationships.putAll(relationships);
    }
  }

  @Override
  String getProviderName() {
    AwsProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${ReservationReportCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  public Collection<NetflixAmazonCredentials> getAccounts() {
    return accounts;
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    long startTime = System.currentTimeMillis()
    log.info("Describing items in ${agentType}")

    ConcurrentHashMap<String, OverallReservationDetail> reservations = new ConcurrentHashMap<>()
    Observable
      .from(accounts.sort { it.name })
      .flatMap({ credential ->
        extractReservations(reservations, credential).subscribeOn(scheduler)
    })
      .observeOn(scheduler)
      .toList()
      .toBlocking()
      .single()

    def amazonReservationReport = new AmazonReservationReport(start: new Date(startTime), end: new Date())
    accounts.each { NetflixAmazonCredentials credentials ->
      amazonReservationReport.accounts << [
        accountId: credentials.accountId,
        name     : credentials.name,
        regions  : credentials.regions*.name
      ]
    }

    amazonReservationReport.reservations = reservations.values().sort {
      a, b -> a.availabilityZone <=> b.availabilityZone ?: a.instanceType <=> b.instanceType ?: a.os <=> b.os
    }
    log.info("Caching ${reservations.size()} items in ${agentType} took ${System.currentTimeMillis() - startTime}ms")

    // v1 is a legacy report that does not differentiate between vpc and non-vpc reserved instances
    accountReservationDetailSerializer.mergeVpcReservations = true
    def v1 = objectMapper.convertValue(amazonReservationReport, Map)

    accountReservationDetailSerializer.mergeVpcReservations = false
    def v2 = objectMapper.convertValue(amazonReservationReport, Map)

    metricsSupport.registerMetrics(objectMapper.convertValue(v2, AmazonReservationReport))

    return new DefaultCacheResult(
      (RESERVATION_REPORTS.ns): [
        new MutableCacheData("v1", ["report": v1], [:]),
        new MutableCacheData("v2", ["report": v2], [:])
      ]
    )
  }

  Observable extractReservations(ConcurrentHashMap<String, OverallReservationDetail> reservations,
                                 NetflixAmazonCredentials credentials) {
    def getReservation = { String availabilityZone, String operatingSystemType, String instanceType ->
      def key = [availabilityZone, operatingSystemType, instanceType].join(":")
      def newOverallReservationDetail = new OverallReservationDetail(
        availabilityZone: availabilityZone,
        os: AmazonReservationReport.OperatingSystemType.valueOf(operatingSystemType as String).name,
        instanceType: instanceType
      )

      def existingOverallReservationDetail = reservations.putIfAbsent(key, newOverallReservationDetail)
      if (existingOverallReservationDetail) {
        return existingOverallReservationDetail
      }

      return newOverallReservationDetail
    }

    Observable
      .from(credentials.regions)
      .flatMap({ AmazonCredentials.AWSRegion region ->
        log.info("Fetching reservation report for ${credentials.name}:${region.name}")

        def amazonEC2 = amazonClientProvider.getAmazonEC2(credentials, region.name)

        long startTime = System.currentTimeMillis()
        def cacheView = getCacheView()
        def reservedInstances = cacheView.getAll(
          RESERVED_INSTANCES.ns,
          cacheView.filterIdentifiers(RESERVED_INSTANCES.ns, Keys.getReservedInstancesKey('*', credentials.name, region.name))
        ).collect {
          objectMapper.convertValue(it.attributes, ReservedInstanceDetails)
        }
        log.debug("Took ${System.currentTimeMillis() - startTime}ms to describe reserved instances for ${credentials.name}/${region.name}")

        reservedInstances.findAll {
          it.state.equalsIgnoreCase("active") &&
            ["Heavy Utilization", "Partial Upfront", "All Upfront", "No Upfront"].contains(it.offeringType)
        }.each {
          def osType = operatingSystemType(it.productDescription)
          def reservation = getReservation(it.availabilityZone, osType.name, it.instanceType)
          reservation.totalReserved.addAndGet(it.instanceCount)

          if (osType.isVpc || vpcOnlyAccounts.contains(credentials.name)) {
            reservation.getAccount(credentials.name).reservedVpc.addAndGet(it.instanceCount)
          } else {
            reservation.getAccount(credentials.name).reserved.addAndGet(it.instanceCount)
          }
        }

        startTime = System.currentTimeMillis()
        def describeInstancesRequest = new DescribeInstancesRequest().withMaxResults(500)
        def allowedStates = ["pending", "running"] as Set<String>
        while (true) {
          def result = amazonEC2.describeInstances(describeInstancesRequest)
          result.reservations.each {
            it.getInstances().each {
              if (!allowedStates.contains(it.state.name.toLowerCase())) {
                return
              }

              def osTypeName = operatingSystemType(it.platform ? "Windows" : "Linux/UNIX").name
              def reservation = getReservation(it.placement.availabilityZone, osTypeName, it.instanceType)
              reservation.totalUsed.incrementAndGet()

              if (it.vpcId) {
                reservation.getAccount(credentials.name).usedVpc.incrementAndGet()
              } else {
                reservation.getAccount(credentials.name).used.incrementAndGet()
              }
            }
          }

          if (result.nextToken) {
            describeInstancesRequest.withNextToken(result.nextToken)
          } else {
            break
          }
        }
        log.debug("Took ${System.currentTimeMillis() - startTime}ms to describe instances for ${credentials.name}/${region.name}")

      return Observable.empty()
    })
  }

  static AmazonReservationReport.OperatingSystemType operatingSystemType(String productDescription) {
    switch (productDescription.toUpperCase()) {
      case "Linux/UNIX".toUpperCase():
        return AmazonReservationReport.OperatingSystemType.LINUX
      case "Linux/UNIX (Amazon VPC)".toUpperCase():
        return AmazonReservationReport.OperatingSystemType.LINUX_VPC
      case "Windows".toUpperCase():
        return AmazonReservationReport.OperatingSystemType.WINDOWS
      case "Windows (Amazon VPC)".toUpperCase():
        return AmazonReservationReport.OperatingSystemType.WINDOWS_VPC
      case "Red Hat Enterprise Linux".toUpperCase():
        return AmazonReservationReport.OperatingSystemType.RHEL
      case "Windows with SQL Server Standard".toUpperCase():
        return AmazonReservationReport.OperatingSystemType.WINDOWS_SQL_SERVER
      default:
        log.error("Unknown product description (${productDescription})")
        return AmazonReservationReport.OperatingSystemType.UNKNOWN
    }
  }

  private Cache getCacheView() {
    if (!this.cacheView) {
      this.cacheView = ctx.getBean(Cache)
    }
    this.cacheView
  }

  static class AccountReservationDetailSerializer extends JsonSerializer<AmazonReservationReport.AccountReservationDetail> {
    ObjectMapper objectMapper = new ObjectMapper()
    boolean mergeVpcReservations


    @Override
    void serialize(AmazonReservationReport.AccountReservationDetail value,
                   JsonGenerator gen,
                   SerializerProvider serializers) throws IOException, JsonProcessingException {
      if (mergeVpcReservations) {
        value = new AmazonReservationReport.AccountReservationDetail(
          reserved: new AtomicInteger(value.reserved.intValue() + value.reservedVpc.intValue()),
          used: new AtomicInteger(value.used.intValue() + value.usedVpc.intValue()),
          reservedVpc: null,
          usedVpc: null
        )
      }

      gen.writeObject(objectMapper.convertValue(value, Map))
    }
  }

  static class ReservedInstanceDetails {
    String state
    String offeringType
    String productDescription
    String availabilityZone
    String instanceType
    int instanceCount
  }

  static class MetricsSupport {
    private final LoadingCache<String, AmazonReservationReport> reservationReportCache = CacheBuilder.newBuilder()
      .concurrencyLevel(1)
      .weakKeys()
      .maximumSize(1)
      .expireAfterWrite(30, TimeUnit.SECONDS)
      .build(
      new CacheLoader<String, AmazonReservationReport>() {
        public AmazonReservationReport load(String key) {
          return objectMapper.convertValue(
            cache.call().get(RESERVATION_REPORTS.ns, "v2").attributes["report"] as Map,
            AmazonReservationReport
          )
        }
      });

    private final Map<String, Id> existingMetricIds = new ConcurrentHashMap<>()
    private final ObjectMapper objectMapper
    private final Registry registry
    private final Closure<Cache> cache

    MetricsSupport(ObjectMapper objectMapper, Registry registry, Closure<Cache> cache) {
      this.registry = registry
      this.objectMapper = objectMapper
      this.cache = cache
    }

    private void registerMetric(String name, Map<String, String> tags, Closure metricValueClosure) {
      def id = registry.createId(name).withTags(tags)
      def existingId = existingMetricIds.putIfAbsent(name + ":" + tags.values().sort().join(":"), id)

      if (!existingId) {
        registry.gauge(id, reservationReportCache, { LoadingCache<String, AmazonReservationReport> reservationReportCache ->
          def overallReservationDetail = reservationReportCache.get("v2").reservations.find {
            it.availabilityZone == tags.availabilityZone && it.instanceType == tags.instanceType && it.os.name == tags.os
          }
          return metricValueClosure.call(overallReservationDetail)
        } as ToDoubleFunction)
      }
    }

    void registerMetrics(AmazonReservationReport reservationReport) {
      reservationReport.reservations.each { OverallReservationDetail overallReservationDetail ->
        def baseTags = [
          availabilityZone: overallReservationDetail.availabilityZone,
          instanceType    : overallReservationDetail.instanceType,
          os              : overallReservationDetail.os.name
        ] as Map<String, String>

        registerMetric("reservedInstances.surplusOverall", baseTags, { OverallReservationDetail o ->
          return (o?.totalSurplus() ?: 0) as Double
        })

        overallReservationDetail.accounts.each { String accountName, AmazonReservationReport.AccountReservationDetail reservationDetail ->
          registerMetric("reservedInstances.surplusByAccountVpc", baseTags + ["account": accountName], { OverallReservationDetail o ->
            return (o?.accounts?.get(accountName)?.surplusVpc() ?: 0) as Double
          })

          registerMetric("reservedInstances.surplusByAccountClassic", baseTags + ["account": accountName], { OverallReservationDetail o ->
            return (o?.accounts?.get(accountName)?.surplus() ?: 0) as Double
          })
        }
      }
    }
  }
}
