/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.handler

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.BasicOracleBMCSDeployDescription
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSServerGroup
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.oraclebmcs.service.servergroup.OracleBMCSServerGroupService
import spock.lang.Specification

/**
 * Created by slord on 18/04/2017.
 */
class BasicOracleBMCSDeployHandlerSpec extends Specification {

  def "Handles correct description"(def desc, def result) {
    setup:
    def handler = new BasicOracleBMCSDeployHandler()

    expect:
    handler.handles(desc) == result

    where:
    desc                                   || result
    new BasicOracleBMCSDeployDescription() || true
    Mock(DeployDescription)                || false
  }

  def "Create server group"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.compartmentId >> "ocid.compartment.123"
    TaskRepository.threadLocalTask.set(Mock(Task))
    def desc = new BasicOracleBMCSDeployDescription()
    desc.credentials = creds
    desc.capacity = new ServerGroup.Capacity(desired: 3)
    desc.application = "foo"
    desc.stack = "dev"
    desc.region = "us-phoenix-1"
    def sgService = Mock(OracleBMCSServerGroupService)
    def sg = new OracleBMCSServerGroup(launchConfig: ["createdTime": System.currentTimeMillis()])
    def handler = new BasicOracleBMCSDeployHandler()
    handler.oracleBMCSServerGroupService = sgService

    when:
    def res = handler.handle(desc, null)

    then:
    1 * sgService.listServerGroupNamesByClusterName(_, "foo-dev") >> ["foo-dev-v001"]
    1 * sgService.getServerGroup(creds, "foo", "foo-dev-v001") >> sg
    1 * sgService.createServerGroup(_)
    res != null
    res.serverGroupNames == ["us-phoenix-1:foo-dev-v002"]
  }
}
