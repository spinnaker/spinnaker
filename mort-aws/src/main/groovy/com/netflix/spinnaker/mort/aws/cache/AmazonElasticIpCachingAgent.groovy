package com.netflix.spinnaker.mort.aws.cache

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.Address
import com.netflix.spinnaker.mort.aws.model.AmazonElasticIp
import com.netflix.spinnaker.mort.model.CacheService
import com.netflix.spinnaker.mort.model.CachingAgent
import groovy.transform.Immutable
import groovy.util.logging.Slf4j
import rx.Observable

@Immutable(knownImmutables = ["ec2", "cacheService"])
@Slf4j
class AmazonElasticIpCachingAgent implements CachingAgent {
  final String account
  final String region
  final AmazonEC2 ec2
  final CacheService cacheService

  @Override
  String getDescription() {
    "[$account:$region:eip]"
  }

  @Override
  int getIntervalMultiplier() {
    1
  }

  @Override
  void call() {
    log.info "$description - Caching..."
    def allElasticIps = ec2.describeAddresses().getAddresses()
    Observable.from(allElasticIps).subscribe {
      cacheService.put(Keys.getElasticIpKey(it.publicIp, region, account), new AmazonElasticIp(
          address: it.publicIp,
          domain: it.domain,
          attachedToId: it.instanceId,
          accountName: account,
          region: region
      ))
    }

    evictDeletedElasticIps(allElasticIps)
  }

  void evictDeletedElasticIps(List<Address> currentElasticIps) {
    def relevantKeys = cacheService.keysByType(Keys.Namespace.ELASTIC_IPS.ns).findAll {
      def parts = Keys.parse(it)
      parts.account == account && parts.region == region
    }
    relevantKeys.each { relevantKey ->
      def parts = Keys.parse(relevantKey)
      def match = currentElasticIps.find {
        it.publicIp == parts.address
      }
      if (!match) {
        log.info("Elastic ip '${relevantKey}' not found; removing from cache")
        cacheService.free(relevantKey)
      }
    }
  }
}
