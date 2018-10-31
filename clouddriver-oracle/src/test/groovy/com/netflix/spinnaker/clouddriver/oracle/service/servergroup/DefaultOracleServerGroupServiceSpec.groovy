/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.service.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.oracle.model.OracleInstance
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.oracle.bmc.Region
import com.oracle.bmc.core.ComputeClient
import com.oracle.bmc.core.model.Instance
import com.oracle.bmc.core.requests.LaunchInstanceRequest
import com.oracle.bmc.core.responses.LaunchInstanceResponse
import spock.lang.Specification

class DefaultOracleServerGroupServiceSpec extends Specification {

  def "create server group"() {
    setup:
    def SSHKeys = "ssh-rsa ABC a@b"
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def persistence = Mock(OracleServerGroupPersistence)
    def task = Mock(Task)
    def sgService = new DefaultOracleServerGroupService(persistence)

    when:
    def sg = new OracleServerGroup(
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
        "sshAuthorizedKeys" : SSHKeys,
        "createdTime"       : System.currentTimeMillis()
      ],
      targetSize: 4,
      credentials: creds
    )
    sgService.createServerGroup(task, sg)

    then:
    4 * creds.computeClient.launchInstance(_) >> { args ->
      LaunchInstanceRequest argumentRequest = (LaunchInstanceRequest) args[0]
      assert argumentRequest.getLaunchInstanceDetails().getMetadata().get("ssh_authorized_keys") == SSHKeys
      return LaunchInstanceResponse.builder().instance(Instance.builder().timeCreated(new Date()).build()).build()
    }
    1 * persistence.upsertServerGroup(_)
  }
  
  def "create server group over limit"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def persistence = Mock(OracleServerGroupPersistence)
    def task = Mock(Task)
    def sgService = new DefaultOracleServerGroupService(persistence)

    when:
    def sg = new OracleServerGroup(
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
      targetSize: 3,
      credentials: creds
    )
    sgService.createServerGroup(task, sg)

    then:
    3 * creds.computeClient.launchInstance(_) >> launchResponse() >> launchResponse() >> 
      { throw new com.oracle.bmc.model.BmcException(400, 'LimitExceeded', 'LimitExceeded', 'LimitExceeded')  }
    1 * persistence.upsertServerGroup(_) >> { args ->
      OracleServerGroup serverGroup = (OracleServerGroup) args[0]
      assert serverGroup.instances.size() == 2
    }
  }

  def "resize (increase) server group"() {
    setup:
    def SSHKeys = null
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
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
        "sshAuthorizedKeys" : SSHKeys,
        "createdTime"       : System.currentTimeMillis()
      ],
      instances: [
        new OracleInstance(name: "a")
      ],
      targetSize: 1,
      credentials: creds
    )

    when:
    def resized = sgService.resizeServerGroup(task, creds, "sg1", 5)

    then:
    4 * creds.computeClient.launchInstance(_)  >> { args ->
      LaunchInstanceRequest argumentRequest = (LaunchInstanceRequest) args[0]
      assert argumentRequest.getLaunchInstanceDetails().getMetadata().get("ssh_authorized_keys") == SSHKeys
      return LaunchInstanceResponse.builder().instance(Instance.builder().timeCreated(new Date()).build()).build()
    }
    1 * persistence.getServerGroupByName(_, "sg1") >> sg
    1 * persistence.upsertServerGroup(_) >> { args ->
      OracleServerGroup serverGroup = (OracleServerGroup) args[0]
      assert serverGroup.instances.size() == 5
      assert serverGroup.targetSize == 5
    }
    resized == true
  }

  def "resize (increase) server group over limit"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
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
        new OracleInstance(name: "a")
      ],
      targetSize: 1,
      credentials: creds
    )

    when:
    def resized = sgService.resizeServerGroup(task, creds, "sg1", 5)

    then:
    4 * creds.computeClient.launchInstance(_) >> 
      launchResponse() >> 
      launchResponse() >>
      launchResponse() >>
      { throw new com.oracle.bmc.model.BmcException(400, 'LimitExceeded', 'LimitExceeded', 'LimitExceeded')  }
    1 * persistence.getServerGroupByName(_, "sg1") >> sg
    1 * persistence.upsertServerGroup(_) >> { args ->
      OracleServerGroup serverGroup = (OracleServerGroup) args[0]
      assert serverGroup.instances.size() == 4
      assert serverGroup.targetSize == 4
    }
    resized == true
  }

  def "resize (decrease) server group"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
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
        new OracleInstance(name: "a"),
        new OracleInstance(name: "b"),
        new OracleInstance(name: "c"),
        new OracleInstance(name: "d"),
        new OracleInstance(name: "e")
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
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
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
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
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
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
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
        new OracleInstance(name: "a"),
        new OracleInstance(name: "b"),
        new OracleInstance(name: "c"),
        new OracleInstance(name: "d"),
        new OracleInstance(name: "e")
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
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
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
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
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
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)

    when:
    def serverGroups = sgService.listServerGroupNamesByClusterName(creds, "foo-test")

    then:
    1 * persistence.listServerGroupNames(_) >> ["foo-test-v001", "foo-v002", "foo-edge-v001", "foo-test-v002", "bar-v001"]
    serverGroups == ["foo-test-v001", "foo-test-v002"]
  }
  
  LaunchInstanceResponse launchResponse() {
    LaunchInstanceResponse.builder().instance(
      Instance.builder().timeCreated(new Date()).build()).build()
  }
}
