import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import { AWS_LOAD_BALANCER_MODULE } from './loadBalancer/loadBalancer.module';
import { AWS_REACT_MODULE } from './reactShims/aws.react.module';
import { AWS_SECURITY_GROUP_MODULE } from './securityGroup/securityGroup.module';
import { AWS_SERVER_GROUP_TRANSFORMER } from './serverGroup/serverGroup.transformer';
import './validation/ApplicationNameValidator';
import { VPC_MODULE } from './vpc/vpc.module';
import { SUBNET_RENDERER } from './subnet/subnet.renderer';
import { SERVER_GROUP_DETAILS_MODULE } from './serverGroup/details/serverGroupDetails.module';
import { COMMON_MODULE } from './common/common.module';
import './help/amazon.help';

import { AwsImageReader } from './image';
import { AmazonLoadBalancerClusterContainer } from './loadBalancer/AmazonLoadBalancerClusterContainer';
import { AmazonLoadBalancersTag } from './loadBalancer/AmazonLoadBalancersTag';

import './deploymentStrategy/rollingPush.strategy';

import './logo/aws.logo.less';
import { AmazonCloneServerGroupModal } from './serverGroup/configure/wizard/AmazonCloneServerGroupModal';
import { AmazonLoadBalancerChoiceModal } from './loadBalancer/configure/AmazonLoadBalancerChoiceModal';

import { AmazonServerGroupActions } from './serverGroup/details/AmazonServerGroupActions';
import { amazonServerGroupDetailsGetter } from './serverGroup/details/amazonServerGroupDetailsGetter';

import {
  AdvancedSettingsDetailsSection,
  AmazonInfoDetailsSection,
  CapacityDetailsSection,
  HealthDetailsSection,
  LaunchConfigDetailsSection,
  LogsDetailsSection,
  PackageDetailsSection,
  ScalingPoliciesDetailsSection,
  ScalingProcessesDetailsSection,
  ScheduledActionsDetailsSection,
  SecurityGroupsDetailsSection,
  TagsDetailsSection,
} from './serverGroup/details/sections';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const AMAZON_MODULE = 'spinnaker.amazon';
module(AMAZON_MODULE, [
  AWS_REACT_MODULE,
  require('./pipeline/stages/bake/awsBakeStage').name,
  require('./pipeline/stages/cloneServerGroup/awsCloneServerGroupStage').name,
  require('./pipeline/stages/destroyAsg/awsDestroyAsgStage').name,
  require('./pipeline/stages/disableAsg/awsDisableAsgStage').name,
  require('./pipeline/stages/disableCluster/awsDisableClusterStage').name,
  require('./pipeline/stages/rollbackCluster/awsRollbackClusterStage').name,
  require('./pipeline/stages/enableAsg/awsEnableAsgStage').name,
  require('./pipeline/stages/findAmi/awsFindAmiStage').name,
  require('./pipeline/stages/findImageFromTags/awsFindImageFromTagsStage').name,
  require('./pipeline/stages/modifyScalingProcess/modifyScalingProcessStage').name,
  require('./pipeline/stages/resizeAsg/awsResizeAsgStage').name,
  require('./pipeline/stages/scaleDownCluster/awsScaleDownClusterStage').name,
  require('./pipeline/stages/shrinkCluster/awsShrinkClusterStage').name,
  require('./pipeline/stages/tagImage/awsTagImageStage').name,
  SERVER_GROUP_DETAILS_MODULE,
  COMMON_MODULE,
  AWS_SERVER_GROUP_TRANSFORMER,
  require('./instance/awsInstanceType.service').name,
  AWS_LOAD_BALANCER_MODULE,
  require('./instance/details/instance.details.controller').name,
  AWS_SECURITY_GROUP_MODULE,
  SUBNET_RENDERER,
  VPC_MODULE,
  require('./cache/cacheConfigurer.service').name,
  require('./search/searchResultFormatter').name,
]).config(() => {
  CloudProviderRegistry.registerProvider('aws', {
    name: 'Amazon',
    logo: {
      path: require('./logo/amazon.logo.svg'),
    },
    cache: {
      configurer: 'awsCacheConfigurer',
    },
    image: {
      reader: AwsImageReader,
    },
    serverGroup: {
      transformer: 'awsServerGroupTransformer',
      detailsActions: AmazonServerGroupActions,
      detailsGetter: amazonServerGroupDetailsGetter,
      detailsSections: [
        AmazonInfoDetailsSection,
        CapacityDetailsSection,
        HealthDetailsSection,
        LaunchConfigDetailsSection,
        SecurityGroupsDetailsSection,
        ScalingProcessesDetailsSection,
        ScalingPoliciesDetailsSection,
        ScheduledActionsDetailsSection,
        TagsDetailsSection,
        PackageDetailsSection,
        AdvancedSettingsDetailsSection,
        LogsDetailsSection,
      ],
      CloneServerGroupModal: AmazonCloneServerGroupModal,
      commandBuilder: 'awsServerGroupCommandBuilder',
      configurationService: 'awsServerGroupConfigurationService',
      scalingActivitiesEnabled: true,
    },
    instance: {
      instanceTypeService: 'awsInstanceTypeService',
      detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
      detailsController: 'awsInstanceDetailsCtrl',
    },
    loadBalancer: {
      transformer: 'awsLoadBalancerTransformer',
      detailsTemplateUrl: require('./loadBalancer/details/loadBalancerDetails.html'),
      detailsController: 'awsLoadBalancerDetailsCtrl',
      CreateLoadBalancerModal: AmazonLoadBalancerChoiceModal,
      targetGroupDetailsTemplateUrl: require('./loadBalancer/details/targetGroupDetails.html'),
      targetGroupDetailsController: 'awsTargetGroupDetailsCtrl',
      ClusterContainer: AmazonLoadBalancerClusterContainer,
      LoadBalancersTag: AmazonLoadBalancersTag,
    },
    securityGroup: {
      transformer: 'awsSecurityGroupTransformer',
      reader: 'awsSecurityGroupReader',
      detailsTemplateUrl: require('./securityGroup/details/securityGroupDetail.html'),
      detailsController: 'awsSecurityGroupDetailsCtrl',
      createSecurityGroupTemplateUrl: require('./securityGroup/configure/createSecurityGroup.html'),
      createSecurityGroupController: 'awsCreateSecurityGroupCtrl',
    },
    subnet: {
      renderer: 'awsSubnetRenderer',
    },
    search: {
      resultFormatter: 'awsSearchResultFormatter',
    },
    applicationProviderFields: {
      templateUrl: require('./applicationProviderFields/awsFields.html'),
    },
  });
});

DeploymentStrategyRegistry.registerProvider('aws', ['custom', 'redblack', 'rollingpush', 'rollingredblack']);
