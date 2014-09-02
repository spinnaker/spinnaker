package com.netflix.spinnaker.mort.aws.model

import com.netflix.spinnaker.mort.aws.cache.Keys
import com.netflix.spinnaker.mort.model.CacheService
import com.netflix.spinnaker.mort.model.SecurityGroup
import com.netflix.spinnaker.mort.model.SecurityGroupProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * A provider for amazon security groups
 */
@Component
class AmazonSecurityGroupProvider implements SecurityGroupProvider {

  @Autowired
  CacheService cacheService

  @Override
  Set<SecurityGroup> getAll() {
    def keys = cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS)
    (List<AmazonSecurityGroup>) keys.collect { retrieve(it) }
  }

  @Override
  Set<SecurityGroup> getAllByRegion(String region) {
    def keys = cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS).findAll { key ->
      def parts = Keys.parse(key)
      parts.region == region
    }
    keys.collect { key ->
      retrieve(key)
    }
  }

  @Override
  Set<SecurityGroup> getAllByAccount(String account) {
    def keys = cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS).findAll { key ->
      def parts = Keys.parse(key)
      parts.account == account
    }
    keys.collect { key ->
      retrieve(key)
    }
  }

  @Override
  Set<SecurityGroup> getAllByAccountAndRegion(String account, String region) {
    def keys = cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS).findAll { key ->
      def parts = Keys.parse(key)
      parts.account == account && parts.region == region
    }
    keys.collect { key ->
      retrieve(key)
    }
  }

  @Override
  SecurityGroup get(String account, String name) {
    def keys = cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS).findAll { key ->
      def parts = Keys.parse(key)
      parts.account == account && parts.id == name
    }
    keys.empty ? null : retrieve(keys.first())
  }

  private SecurityGroup retrieve(String key) {
    cacheService.retrieve(key, AmazonSecurityGroup)
  }

}
