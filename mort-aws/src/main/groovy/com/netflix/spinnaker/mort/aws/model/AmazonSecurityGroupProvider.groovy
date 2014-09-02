package com.netflix.spinnaker.mort.aws.model

import com.netflix.spinnaker.mort.aws.cache.Keys
import com.netflix.spinnaker.mort.model.CacheService
import com.netflix.spinnaker.mort.model.SecurityGroup
import com.netflix.spinnaker.mort.model.SecurityGroupProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 *
 */
@Component
class AmazonSecurityGroupProvider implements SecurityGroupProvider {

  @Autowired
  CacheService cacheService

  @Override
  Set<SecurityGroup> getAll() {
    def keys = cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS)
    (List<AmazonSecurityGroup>) keys.collect { cacheService.retrieve(it, AmazonSecurityGroup) }
  }

  @Override
  Set<SecurityGroup> getAllByRegion(String region) {
    return null
  }

  @Override
  Set<SecurityGroup> getAllByAccount(String account) {
    return null
  }

  @Override
  Set<SecurityGroup> getAllByAccountAndRegion(String account, String region) {
    return null
  }

  @Override
  SecurityGroup get(String account, String name) {
    return null
  }

}
