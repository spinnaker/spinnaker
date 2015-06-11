package com.netflix.spinnaker.mort.aws.model

import com.netflix.spinnaker.mort.aws.cache.Keys
import com.netflix.spinnaker.mort.model.CacheService
import com.netflix.spinnaker.mort.model.ElasticIpProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AmazonElasticIpProvider implements ElasticIpProvider<AmazonElasticIp> {
  @Autowired
  CacheService cacheService

  @Override
  Set<AmazonElasticIp> getAllByAccount(String account) {
    def keys = cacheService.keysByType(Keys.Namespace.ELASTIC_IPS).findAll { key ->
        Keys.parse(key).account == account
    }

    return keys.collect {
      cacheService.retrieve(it, AmazonElasticIp)
    }
  }

  @Override
  Set<AmazonElasticIp> getAllByAccountAndRegion(String account, String region) {
    def keys = cacheService.keysByType(Keys.Namespace.ELASTIC_IPS).findAll { key ->
      def parsedKey = Keys.parse(key)
      return parsedKey.account == account && parsedKey.region == region
    }

    return keys.collect {
      cacheService.retrieve(it, AmazonElasticIp)
    }
  }
}
