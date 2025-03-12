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
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials

interface OracleServerGroupService {

  public List<OracleServerGroup> listAllServerGroups(OracleNamedAccountCredentials creds)

  public List<String> listServerGroupNamesByClusterName(OracleNamedAccountCredentials creds, String clusterName)

  public OracleServerGroup getServerGroup(OracleNamedAccountCredentials creds, String application, String name)

  public void createServerGroup(Task task, OracleServerGroup serverGroup)

  public boolean destroyServerGroup(Task task, OracleNamedAccountCredentials creds, String serverGroupName)

  public boolean resizeServerGroup(Task task, OracleNamedAccountCredentials creds, String serverGroupName, Integer targetSize)

  public void disableServerGroup(Task task, OracleNamedAccountCredentials creds, String serverGroupName)

  public void enableServerGroup(Task task, OracleNamedAccountCredentials creds, String serverGroupName)

  public void updateServerGroup(OracleServerGroup sg)
}
