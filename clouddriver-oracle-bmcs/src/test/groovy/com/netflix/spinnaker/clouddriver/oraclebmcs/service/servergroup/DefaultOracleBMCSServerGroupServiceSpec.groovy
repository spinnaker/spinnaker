/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.service.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSInstance
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSServerGroup
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.oracle.bmc.Region
import com.oracle.bmc.core.ComputeClient
import com.oracle.bmc.core.model.Instance
import com.oracle.bmc.core.responses.LaunchInstanceResponse
import spock.lang.Specification

class DefaultOracleBMCSServerGroupServiceSpec extends Specification {

  def "create server group"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def persistence = Mock(OracleBMCSServerGroupPersistence)
    def sgService = new DefaultOracleBMCSServerGroupService(persistence)

    when:
    def sg = new OracleBMCSServerGroup(
      name: "sg1",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "createdTime"       : System.currentTimeMillis()
      ],
      targetSize: 4,
      credentials: creds
    )
    sgService.createServerGroup(sg)

    then:
    4 * creds.computeClient.launchInstance(_) >> LaunchInstanceResponse.builder().instance(
      Instance.builder().timeCreated(new Date()).build()
    ).build()
    1 * persistence.upsertServerGroup(_)
  }

  def "resize (increase) server group"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleBMCSServerGroupPersistence)
    def sgService = new DefaultOracleBMCSServerGroupService(persistence)
    def sg = new OracleBMCSServerGroup(
      name: "sg1",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "createdTime"       : System.currentTimeMillis()
      ],
      targetSize: 1,
      credentials: creds
    )

    when:
    def resized = sgService.resizeServerGroup(task, creds, "sg1", 5)

    then:
    4 * creds.computeClient.launchInstance(_) >> LaunchInstanceResponse.builder().instance(
      Instance.builder().timeCreated(new Date()).build()
    ).build()
    1 * persistence.getServerGroupByName(_, "sg1") >> sg
    1 * persistence.upsertServerGroup(_)
    resized == true
  }

  def "resize (decrease) server group"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleBMCSServerGroupPersistence)
    def sgService = new DefaultOracleBMCSServerGroupService(persistence)
    def sg = new OracleBMCSServerGroup(
      name: "sg1",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "createdTime"       : System.currentTimeMillis()
      ],
      instances: [
        new OracleBMCSInstance(name: "a"),
        new OracleBMCSInstance(name: "b"),
        new OracleBMCSInstance(name: "c"),
        new OracleBMCSInstance(name: "d"),
        new OracleBMCSInstance(name: "e")
      ],
      targetSize: 5,
      credentials: creds
    )

    when:
    def resized = sgService.resizeServerGroup(task, creds, "sg1", 1)

    then:
    4 * creds.computeClient.terminateInstance(_)
    1 * persistence.getServerGroupByName(_, "sg1") >> sg
    1 * persistence.upsertServerGroup(_)
    resized == true
  }

  def "enable server group"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleBMCSServerGroupPersistence)
    def sgService = new DefaultOracleBMCSServerGroupService(persistence)
    def sg = new OracleBMCSServerGroup(
      name: "sg1",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "createdTime"       : System.currentTimeMillis()
      ],
      targetSize: 1,
      credentials: creds,
      disabled: true
    )

    when:
    sgService.enableServerGroup(task, creds, "sg1")

    then:
    1 * persistence.getServerGroupByName(_, "sg1") >> sg
    1 * persistence.upsertServerGroup(_)
    sg.disabled == false
  }

  def "disable server group"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleBMCSServerGroupPersistence)
    def sgService = new DefaultOracleBMCSServerGroupService(persistence)
    def sg = new OracleBMCSServerGroup(
      name: "sg1",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "createdTime"       : System.currentTimeMillis()
      ],
      targetSize: 1,
      credentials: creds,
      disabled: false
    )

    when:
    sgService.disableServerGroup(task, creds, "sg1")

    then:
    1 * persistence.getServerGroupByName(_, "sg1") >> sg
    1 * persistence.upsertServerGroup(_)
    sg.disabled == true
  }

  def "destroy server group"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleBMCSServerGroupPersistence)
    def sgService = new DefaultOracleBMCSServerGroupService(persistence)
    def sg = new OracleBMCSServerGroup(
      name: "sg1",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "createdTime"       : System.currentTimeMillis()
      ],
      instances: [
        new OracleBMCSInstance(name: "a"),
        new OracleBMCSInstance(name: "b"),
        new OracleBMCSInstance(name: "c"),
        new OracleBMCSInstance(name: "d"),
        new OracleBMCSInstance(name: "e")
      ],
      targetSize: 5,
      credentials: creds,
      disabled: false
    )

    when:
    sgService.destroyServerGroup(task, creds, "sg1")

    then:
    1 * persistence.getServerGroupByName(_, "sg1") >> sg
    5 * creds.computeClient.terminateInstance(_)
    1 * persistence.deleteServerGroup(sg)
  }

  def "get server group"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def persistence = Mock(OracleBMCSServerGroupPersistence)
    def sgService = new DefaultOracleBMCSServerGroupService(persistence)
    def sg = new OracleBMCSServerGroup(
      name: "foo-v001",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "createdTime"       : System.currentTimeMillis()
      ],
      targetSize: 5,
      credentials: creds,
      disabled: false
    )

    when:
    sgService.getServerGroup(creds, "foo", "foo-v001")

    then:
    1 * persistence.listServerGroupNames(_) >> ["foo-v001"]
    1 * persistence.getServerGroupByName(_, "foo-v001") >> sg
  }

  def "list all server group"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def persistence = Mock(OracleBMCSServerGroupPersistence)
    def sgService = new DefaultOracleBMCSServerGroupService(persistence)
    def sg = new OracleBMCSServerGroup(
      name: "foo-v001",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "createdTime"       : System.currentTimeMillis()
      ],
      targetSize: 5,
      credentials: creds,
      disabled: false
    )

    when:
    def serverGroups = sgService.listAllServerGroups(creds)

    then:
    1 * persistence.listServerGroupNames(_) >> ["foo-v001", "bar-v001", "bbq-v001", "foo-test-v001"]
    4 * persistence.getServerGroupByName(_, _) >> sg
    serverGroups.size() == 4
  }

  def "list server group names by cluster"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def persistence = Mock(OracleBMCSServerGroupPersistence)
    def sgService = new DefaultOracleBMCSServerGroupService(persistence)

    when:
    def serverGroups = sgService.listServerGroupNamesByClusterName(creds, "foo-test")

    then:
    1 * persistence.listServerGroupNames(_) >> ["foo-test-v001", "foo-v002", "foo-edge-v001", "foo-test-v002", "bar-v001"]
    serverGroups == ["foo-test-v001", "foo-test-v002"]
  }

}
