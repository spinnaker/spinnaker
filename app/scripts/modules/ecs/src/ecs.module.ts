'use strict';

import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import { ECS_SERVER_GROUP_TRANSFORMER } from './serverGroup/serverGroup.transformer';
import { ECS_NETWORKING_SECTION } from './serverGroup/configure/wizard/networking/networkingSelector.component';
import { SERVER_GROUP_DETAILS_MODULE } from './serverGroup/details/serverGroupDetails.module';
import { IAM_ROLE_READ_SERVICE } from './iamRoles/iamRole.read.service';
import { ECS_CLUSTER_READ_SERVICE } from './ecsCluster/ecsCluster.read.service';
import { ECS_SECRET_READ_SERVICE } from './secrets/secret.read.service';
import { METRIC_ALARM_READ_SERVICE } from './metricAlarm/metricAlarm.read.service';
import { PLACEMENT_STRATEGY_SERVICE } from './placementStrategy/placementStrategy.service';
import './ecs.help';
import { COMMON_MODULE } from './common/common.module';
import { ECS_SERVERGROUP_MODULE } from './serverGroup/serverGroup.module';
import { ECS_SERVER_GROUP_LOGGING } from './serverGroup/configure/wizard/logging/logging.component';
import { TASK_DEFINITION_REACT } from './serverGroup/configure/wizard/taskDefinition/TaskDefinition';
import { ECS_SECURITY_GROUP_MODULE } from './securityGroup/securityGroup.module';

import './logo/ecs.logo.less';
import { ECS_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_ECS_CONTROLLER } from './serverGroup/configure/wizard/CloneServerGroup.ecs.controller';
import { ECS_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_ADVANCEDSETTINGS_COMPONENT } from './serverGroup/configure/wizard/advancedSettings/advancedSettings.component';
import { ECS_SERVERGROUP_CONFIGURE_WIZARD_CONTAINER_CONTAINER_COMPONENT } from './serverGroup/configure/wizard/container/container.component';
import { ECS_SERVERGROUP_CONFIGURE_WIZARD_HORIZONTALSCALING_HORIZONTALSCALING_COMPONENT } from './serverGroup/configure/wizard/horizontalScaling/horizontalScaling.component';
import { ECS_SERVERGROUP_CONFIGURE_WIZARD_SERVICEDISCOVERY_SERVICEDISCOVERY_COMPONENT } from './serverGroup/configure/wizard/serviceDiscovery/serviceDiscovery.component';
import { ECS_SERVERGROUP_CONFIGURE_WIZARD_LOCATION_SERVERGROUPBASICSETTINGS_CONTROLLER } from './serverGroup/configure/wizard/location/ServerGroupBasicSettings.controller';
import { ECS_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { ECS_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER } from './instance/details/instance.details.controller';
import { ECS_PIPELINE_STAGES_FINDIMAGEFROMTAGS_ECSFINDIMAGEFROMTAGSTAGE } from './pipeline/stages/findImageFromTags/ecsFindImageFromTagStage';
import { ECS_PIPELINE_STAGES_DESTROYASG_ECSDESTROYASGSTAGE } from './pipeline/stages/destroyAsg/ecsDestroyAsgStage';
import { ECS_PIPELINE_STAGES_DISABLEASG_ECSDISABLEASGSTAGE } from './pipeline/stages/disableAsg/ecsDisableAsgStage';
import { ECS_PIPELINE_STAGES_DISABLECLUSTER_ECSDISABLECLUSTERSTAGE } from './pipeline/stages/disableCluster/ecsDisableClusterStage';
import { ECS_PIPELINE_STAGES_ENABLEASG_ECSENABLEASGSTAGE } from './pipeline/stages/enableAsg/ecsEnableAsgStage';
import { ECS_PIPELINE_STAGES_RESIZEASG_ECSRESIZEASGSTAGE } from './pipeline/stages/resizeAsg/ecsResizeAsgStage';
import { ECS_PIPELINE_STAGES_SCALEDOWNCLUSTER_ECSSCALEDOWNCLUSTERSTAGE } from './pipeline/stages/scaleDownCluster/ecsScaleDownClusterStage';
import { ECS_PIPELINE_STAGES_SHRINKCLUSTER_ECSSHRINKCLUSTERSTAGE } from './pipeline/stages/shrinkCluster/ecsShrinkClusterStage';

require('./ecs.settings');

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const ECS_MODULE = 'spinnaker.ecs';
module(ECS_MODULE, [
  ECS_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_ECS_CONTROLLER,
  SERVER_GROUP_DETAILS_MODULE,
  IAM_ROLE_READ_SERVICE,
  ECS_SERVER_GROUP_TRANSFORMER,
  // require('./pipeline/stages/cloneServerGroup/ecsCloneServerGroupStage').name,  // TODO(Bruno Carrier): We should enable this on Clouddriver before revealing this stage
  ECS_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_ADVANCEDSETTINGS_COMPONENT,
  ECS_SERVERGROUP_CONFIGURE_WIZARD_CONTAINER_CONTAINER_COMPONENT,
  ECS_SERVERGROUP_CONFIGURE_WIZARD_HORIZONTALSCALING_HORIZONTALSCALING_COMPONENT,
  TASK_DEFINITION_REACT,
  ECS_SERVER_GROUP_LOGGING,
  ECS_NETWORKING_SECTION,
  ECS_CLUSTER_READ_SERVICE,
  ECS_SECRET_READ_SERVICE,
  ECS_SERVERGROUP_CONFIGURE_WIZARD_SERVICEDISCOVERY_SERVICEDISCOVERY_COMPONENT,
  METRIC_ALARM_READ_SERVICE,
  PLACEMENT_STRATEGY_SERVICE,
  COMMON_MODULE,
  ECS_SERVERGROUP_CONFIGURE_WIZARD_LOCATION_SERVERGROUPBASICSETTINGS_CONTROLLER,
  ECS_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE,
  ECS_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER,
  ECS_PIPELINE_STAGES_FINDIMAGEFROMTAGS_ECSFINDIMAGEFROMTAGSTAGE,
  ECS_PIPELINE_STAGES_DESTROYASG_ECSDESTROYASGSTAGE,
  ECS_PIPELINE_STAGES_DISABLEASG_ECSDISABLEASGSTAGE,
  ECS_PIPELINE_STAGES_DISABLECLUSTER_ECSDISABLECLUSTERSTAGE,
  ECS_PIPELINE_STAGES_ENABLEASG_ECSENABLEASGSTAGE,
  ECS_PIPELINE_STAGES_RESIZEASG_ECSRESIZEASGSTAGE,
  ECS_PIPELINE_STAGES_SCALEDOWNCLUSTER_ECSSCALEDOWNCLUSTERSTAGE,
  ECS_PIPELINE_STAGES_SHRINKCLUSTER_ECSSHRINKCLUSTERSTAGE,
  ECS_SECURITY_GROUP_MODULE,
  ECS_SERVERGROUP_MODULE,
]).config(function() {
  CloudProviderRegistry.registerProvider('ecs', {
    name: 'EC2 Container Service',
    logo: { path: require('./logo/ecs.logo.svg') },
    serverGroup: {
      transformer: 'ecsServerGroupTransformer',
      detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
      detailsController: 'ecsServerGroupDetailsCtrl',
      cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
      cloneServerGroupController: 'ecsCloneServerGroupCtrl',
      commandBuilder: 'ecsServerGroupCommandBuilder',
      // configurationService: 'ecsServerGroupConfigurationService',
      scalingActivitiesEnabled: false,
    },
    instance: {
      detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
      detailsController: 'ecsInstanceDetailsCtrl',
    },
    securityGroup: {
      transformer: 'ecsSecurityGroupTransformer',
      reader: 'ecsSecurityGroupReader',
      detailsTemplateUrl: require('./securityGroup/details/securityGroupDetail.html'),
      detailsController: 'ecsSecurityGroupDetailsCtrl',
    },
  });
});

DeploymentStrategyRegistry.registerProvider('ecs', ['redblack']);
