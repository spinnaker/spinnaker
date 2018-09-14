'use strict';

const angular = require('angular');

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import { ECS_SERVER_GROUP_TRANSFORMER } from './serverGroup/serverGroup.transformer';
import { ECS_NETWORKING_SECTION } from './serverGroup/configure/wizard/networking/networkingSelector.component';
import { SERVER_GROUP_DETAILS_MODULE } from './serverGroup/details/serverGroupDetails.module';
import { IAM_ROLE_READ_SERVICE } from './iamRoles/iamRole.read.service';
import { ECS_CLUSTER_READ_SERVICE } from './ecsCluster/ecsCluster.read.service';
import { METRIC_ALARM_READ_SERVICE } from './metricAlarm/metricAlarm.read.service';
import { PLACEMENT_STRATEGY_SERVICE } from './placementStrategy/placementStrategy.service';
import './ecs.help';
import { COMMON_MODULE } from './common/common.module';
import { ECS_SERVERGROUP_MODULE } from './serverGroup/serverGroup.module';
import { ECS_SERVER_GROUP_LOGGING } from './serverGroup/configure/wizard/logging/logging.component';

import './logo/ecs.logo.less';

require('./ecs.settings.ts');

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const ECS_MODULE = 'spinnaker.ecs';
angular
  .module(ECS_MODULE, [
    require('./serverGroup/configure/wizard/CloneServerGroup.ecs.controller').name,
    SERVER_GROUP_DETAILS_MODULE,
    IAM_ROLE_READ_SERVICE,
    ECS_SERVER_GROUP_TRANSFORMER,
    // require('./pipeline/stages/cloneServerGroup/ecsCloneServerGroupStage').name,  // TODO(Bruno Carrier): We should enable this on Clouddriver before revealing this stage
    require('./serverGroup/configure/wizard/advancedSettings/advancedSettings.component').name,
    require('./serverGroup/configure/wizard/verticalScaling/verticalScaling.component').name,
    require('./serverGroup/configure/wizard/horizontalScaling/horizontalScaling.component').name,
    ECS_SERVER_GROUP_LOGGING,
    ECS_NETWORKING_SECTION,
    ECS_CLUSTER_READ_SERVICE,
    METRIC_ALARM_READ_SERVICE,
    PLACEMENT_STRATEGY_SERVICE,
    COMMON_MODULE,
    require('./serverGroup/configure/wizard/location/ServerGroupBasicSettings.controller').name,
    require('./serverGroup/configure/serverGroupCommandBuilder.service').name,
    require('./instance/details/instance.details.controller').name,
    require('./pipeline/stages/findImageFromTags/ecsFindImageFromTagStage').name,
    require('./pipeline/stages/destroyAsg/ecsDestroyAsgStage').name,
    require('./pipeline/stages/disableAsg/ecsDisableAsgStage').name,
    require('./pipeline/stages/disableCluster/ecsDisableClusterStage').name,
    require('./pipeline/stages/enableAsg/ecsEnableAsgStage').name,
    require('./pipeline/stages/resizeAsg/ecsResizeAsgStage').name,
    require('./pipeline/stages/scaleDownCluster/ecsScaleDownClusterStage').name,
    require('./pipeline/stages/shrinkCluster/ecsShrinkClusterStage').name,
    require('./securityGroup/securityGroup.transformer.js').name,
    ECS_SERVERGROUP_MODULE,
  ])
  .config(function() {
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
      },
    });
  });

DeploymentStrategyRegistry.registerProvider('ecs', ['redblack']);
