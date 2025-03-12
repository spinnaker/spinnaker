/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.provider.view

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ser.FilterProvider
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.oracle.cache.Keys
import com.oracle.bmc.core.model.Instance
import spock.lang.Specification

class OracleInstanceProviderSpec extends Specification {

  def "get instance"(def account, def region, def id, def res) {
    setup:
    def cache = new InMemoryCache()
    cache.mergeAll(Keys.Namespace.INSTANCES.ns, [
      buildInstanceCacheData(1, "R1", "A1", Instance.LifecycleState.Running),
      buildInstanceCacheData(2, "R2", "A2", Instance.LifecycleState.Starting),
      buildInstanceCacheData(3, "R2", "A2", Instance.LifecycleState.Provisioning)
    ])
    def instanceProvider = new OracleInstanceProvider(cache, new ObjectMapper(), null)

    expect:
    instanceProvider.getInstance(account, region, id)?.name == res.name
    instanceProvider.getInstance(account, region, id)?.healthState == res.healthState
    instanceProvider.getInstance(account, region, id)?.health?.first()?.size() == res.hsize

    where:
    account | region | id                || res
    "A1"    | "R1"   | "ocid.instance.1" || [name: "Instance 1", healthState: HealthState.Up, hsize: 3]
    "A1"    | "R1"   | "Instance 1"      || [name: "Instance 1", healthState: HealthState.Up, hsize: 3]
    "A2"    | "R2"   | "ocid.instance.2" || [name: "Instance 2", healthState: HealthState.Starting, hsize: 3]
    "A2"    | "R2"   | "Instance 2"      || [name: "Instance 2", healthState: HealthState.Starting, hsize: 3]
    "A2"    | "R2"   | "Instance 3"      || [name: "Instance 3", healthState: HealthState.Starting, hsize: 3]
    "A2"    | "R2"   | "Does not exist"  || [name: null, healthState: null, hsize: null]
  }

  def buildInstanceCacheData(def num, def region, def account, Instance.LifecycleState lifecycleState) {
    def name = "Instance $num"
    def ocid = "ocid.instance.$num"
    def instance = Instance.builder()
      .displayName(name)
      .id(ocid)
      .imageId("ocid.image.$num")
      .timeCreated(new Date())
      .shape("small")
      .lifecycleState(lifecycleState)
      .build()
      
    def attributes = new ObjectMapper()
      .setFilterProvider(new SimpleFilterProvider().setFailOnUnknownId(false))
      .convertValue(instance, new TypeReference<Map<String, Object>>() {})

    return new DefaultCacheData(
      Keys.getInstanceKey(account, region, name, ocid),
      attributes as Map<String, Object>,
      [:]
    )
  }

}
