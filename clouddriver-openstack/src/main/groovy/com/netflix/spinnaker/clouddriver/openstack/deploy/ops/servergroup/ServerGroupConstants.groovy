/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup

class ServerGroupConstants {
  public static final String HEAT_SERVER_RESOURCE = "OS::Nova::Server"
  public static final String HEAT_ASG_RESOURCE = "OS::Heat::AutoScalingGroup"
  final static String SERVERGROUP_RESOURCE_NAME = 'servergroup'

  final static String SUBTEMPLATE_FILENAME = 'resource_filename'
  final static String SUBTEMPLATE_SERVER_OUTPUT = 'servergroup_server'
  final static String SUBTEMPLATE_OUTPUT = 'servergroup_resource'
  final static String MEMBERTEMPLATE_OUTPUT = 'servergroup_resource_member'

  //this is the file name of the heat template used to create the auto scaling group,
  //and needs to be loaded into memory as a String
  final static String TEMPLATE_FILE = 'servergroup.yaml'

  //this is the name of the subtemplate referenced by the template,
  //and needs to be loaded into memory as a String
  final static String SUBTEMPLATE_SERVER_FILE = "${SUBTEMPLATE_SERVER_OUTPUT}.yaml"

  //this is the name of the subtemplate referenced by the template,
  //and needs to be loaded into memory as a String
  final static String SUBTEMPLATE_FILE = "${SUBTEMPLATE_OUTPUT}.yaml"

  //this is the name of the member template referenced by the subtemplate,
  //and is contructed on the fly
  final static String MEMBERTEMPLATE_FILE = "${MEMBERTEMPLATE_OUTPUT}.yaml"
}
