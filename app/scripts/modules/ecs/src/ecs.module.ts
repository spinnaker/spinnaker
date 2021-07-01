'use strict';

import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import { COMMON_MODULE } from './common/common.module';
import './ecs.help';
import './ecs.settings';
import { ECS_CLUSTER_READ_SERVICE } from './ecsCluster/ecsCluster.read.service';
import { IAM_ROLE_READ_SERVICE } from './iamRoles/iamRole.read.service';
import { ECS_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER } from './instance/details/instance.details.controller';
import { EcsLoadBalancerClusterContainer } from './loadBalancer/EcsLoadBalancerClusterContainer';
import { EcsLoadBalancerDetails } from './loadBalancer/details/loadBalancerDetails';
import { EcsTargetGroupDetails } from './loadBalancer/details/targetGroupDetails';
import { EcsLoadBalancerTransformer } from './loadBalancer/loadBalancer.transformer';
import { ECS_TARGET_GROUP_STATES } from './loadBalancer/targetGroup.states';
import ecsLogo from './logo/ecs.logo.svg';
import { METRIC_ALARM_READ_SERVICE } from './metricAlarm/metricAlarm.read.service';
import { ECS_PIPELINE_STAGES_DESTROYASG_ECSDESTROYASGSTAGE } from './pipeline/stages/destroyAsg/ecsDestroyAsgStage';
import { ECS_PIPELINE_STAGES_DISABLEASG_ECSDISABLEASGSTAGE } from './pipeline/stages/disableAsg/ecsDisableAsgStage';
import { ECS_PIPELINE_STAGES_DISABLECLUSTER_ECSDISABLECLUSTERSTAGE } from './pipeline/stages/disableCluster/ecsDisableClusterStage';
import { ECS_PIPELINE_STAGES_ENABLEASG_ECSENABLEASGSTAGE } from './pipeline/stages/enableAsg/ecsEnableAsgStage';
import { ECS_PIPELINE_STAGES_FINDIMAGEFROMTAGS_ECSFINDIMAGEFROMTAGSTAGE } from './pipeline/stages/findImageFromTags/ecsFindImageFromTagStage';
import { ECS_PIPELINE_STAGES_RESIZEASG_ECSRESIZEASGSTAGE } from './pipeline/stages/resizeAsg/ecsResizeAsgStage';
import { ECS_PIPELINE_STAGES_SCALEDOWNCLUSTER_ECSSCALEDOWNCLUSTERSTAGE } from './pipeline/stages/scaleDownCluster/ecsScaleDownClusterStage';
import { ECS_PIPELINE_STAGES_SHRINKCLUSTER_ECSSHRINKCLUSTERSTAGE } from './pipeline/stages/shrinkCluster/ecsShrinkClusterStage';
import { PLACEMENT_STRATEGY_SERVICE } from './placementStrategy/placementStrategy.service';
import { ECS_SECRET_READ_SERVICE } from './secrets/secret.read.service';
import { ECS_SECURITY_GROUP_MODULE } from './securityGroup/securityGroup.module';
import { ECS_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { ECS_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_ECS_CONTROLLER } from './serverGroup/configure/wizard/CloneServerGroup.ecs.controller';
import { ECS_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_ADVANCEDSETTINGS_COMPONENT } from './serverGroup/configure/wizard/advancedSettings/advancedSettings.component';
import { ECS_CAPACITY_PROVIDER_REACT } from './serverGroup/configure/wizard/capacityProvider/CapacityProvider';
import { CONTAINER_REACT } from './serverGroup/configure/wizard/container/Container';
import { ECS_SERVERGROUP_CONFIGURE_WIZARD_HORIZONTALSCALING_HORIZONTALSCALING_COMPONENT } from './serverGroup/configure/wizard/horizontalScaling/horizontalScaling.component';
import { ECS_SERVERGROUP_CONFIGURE_WIZARD_LOCATION_SERVERGROUPBASICSETTINGS_CONTROLLER } from './serverGroup/configure/wizard/location/ServerGroupBasicSettings.controller';
import { ECS_SERVER_GROUP_LOGGING } from './serverGroup/configure/wizard/logging/logging.component';
import { ECS_NETWORKING_REACT } from './serverGroup/configure/wizard/networking/Networking';
import { SERVICE_DISCOVERY_REACT } from './serverGroup/configure/wizard/serviceDiscovery/ServiceDiscovery';
import { TASK_DEFINITION_REACT } from './serverGroup/configure/wizard/taskDefinition/TaskDefinition';
import { SERVER_GROUP_DETAILS_MODULE } from './serverGroup/details/serverGroupDetails.module';
import { ECS_SERVERGROUP_MODULE } from './serverGroup/serverGroup.module';
import { ECS_SERVER_GROUP_TRANSFORMER } from './serverGroup/serverGroup.transformer';

import './logo/ecs.logo.less';

export const ECS_MODULE = 'spinnaker.ecs';
module(ECS_MODULE, [
  ECS_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_ECS_CONTROLLER,
  SERVER_GROUP_DETAILS_MODULE,
  IAM_ROLE_READ_SERVICE,
  ECS_SERVER_GROUP_TRANSFORMER,
  // require('./pipeline/stages/cloneServerGroup/ecsCloneServerGroupStage').name,  // TODO(Bruno Carrier): We should enable this on Clouddriver before revealing this stage
  ECS_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_ADVANCEDSETTINGS_COMPONENT,
  ECS_SERVERGROUP_CONFIGURE_WIZARD_HORIZONTALSCALING_HORIZONTALSCALING_COMPONENT,
  TASK_DEFINITION_REACT,
  CONTAINER_REACT,
  ECS_NETWORKING_REACT,
  SERVICE_DISCOVERY_REACT,
  ECS_CAPACITY_PROVIDER_REACT,
  ECS_SERVER_GROUP_LOGGING,
  ECS_CLUSTER_READ_SERVICE,
  ECS_SECRET_READ_SERVICE,
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
  ECS_TARGET_GROUP_STATES,
]).config(function () {
  CloudProviderRegistry.registerProvider('ecs', {
    name: 'EC2 Container Service',
    logo: { path: ecsLogo },
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
    loadBalancer: {
      transformer: EcsLoadBalancerTransformer,
      ClusterContainer: EcsLoadBalancerClusterContainer,
      targetGroupDetails: EcsTargetGroupDetails,
      details: EcsLoadBalancerDetails,
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
