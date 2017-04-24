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
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSServerGroup
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials

interface OracleBMCSServerGroupService {

  public List<OracleBMCSServerGroup> listAllServerGroups(OracleBMCSNamedAccountCredentials creds)

  public List<String> listServerGroupNamesByClusterName(OracleBMCSNamedAccountCredentials creds, String clusterName)

  public OracleBMCSServerGroup getServerGroup(OracleBMCSNamedAccountCredentials creds, String application, String name)

  public void createServerGroup(OracleBMCSServerGroup serverGroup)

  public boolean destroyServerGroup(Task task, OracleBMCSNamedAccountCredentials creds, String serverGroupName)

  public boolean resizeServerGroup(Task task, OracleBMCSNamedAccountCredentials creds, String serverGroupName, Integer targetSize)

  public void disableServerGroup(Task task, OracleBMCSNamedAccountCredentials creds, String serverGroupName)

  public void enableServerGroup(Task task, OracleBMCSNamedAccountCredentials creds, String serverGroupName)

}
