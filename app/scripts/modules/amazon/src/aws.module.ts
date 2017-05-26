import { module } from 'angular';

import { CLOUD_PROVIDER_REGISTRY, CloudProviderRegistry } from '@spinnaker/core';

import { AWS_LOAD_BALANCER_MODULE } from './loadBalancer/loadBalancer.module';
import { AMAZON_APPLICATION_NAME_VALIDATOR } from './validation/applicationName.validator';
import { VPC_MODULE } from './vpc/vpc.module';
import { SUBNET_RENDERER } from './subnet/subnet.renderer';
import { AWS_SECURITY_GROUP_MODULE } from './securityGroup/securityGroup.module';
import { SERVER_GROUP_DETAILS_MODULE } from './serverGroup/details/serverGroupDetails.module';
import { COMMON_MODULE } from './common/common.module';
import { AMAZON_HELP } from './help/amazon.help';

import { AmazonLoadBalancerClusterContainer } from './loadBalancer/AmazonLoadBalancerClusterContainer';
import { AmazonLoadBalancersTag } from './loadBalancer/AmazonLoadBalancersTag';

import './logo/aws.logo.less';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const AMAZON_MODULE = 'spinnaker.amazon';
module(AMAZON_MODULE, [
  CLOUD_PROVIDER_REGISTRY,
  AMAZON_HELP,
  AMAZON_APPLICATION_NAME_VALIDATOR,
  require('./pipeline/stages/bake/awsBakeStage'),
  require('./pipeline/stages/cloneServerGroup/awsCloneServerGroupStage'),
  require('./pipeline/stages/destroyAsg/awsDestroyAsgStage'),
  require('./pipeline/stages/disableAsg/awsDisableAsgStage'),
  require('./pipeline/stages/disableCluster/awsDisableClusterStage'),
  require('./pipeline/stages/enableAsg/awsEnableAsgStage'),
  require('./pipeline/stages/findAmi/awsFindAmiStage'),
  require('./pipeline/stages/findImageFromTags/awsFindImageFromTagsStage'),
  require('./pipeline/stages/modifyScalingProcess/modifyScalingProcessStage'),
  require('./pipeline/stages/resizeAsg/awsResizeAsgStage'),
  require('./pipeline/stages/scaleDownCluster/awsScaleDownClusterStage'),
  require('./pipeline/stages/shrinkCluster/awsShrinkClusterStage'),
  require('./pipeline/stages/tagImage/awsTagImageStage'),
  SERVER_GROUP_DETAILS_MODULE,
  COMMON_MODULE,
  require('./serverGroup/serverGroup.transformer'),
  require('./serverGroup/configure/wizard/CloneServerGroup.aws.controller'),
  require('./instance/awsInstanceType.service'),
  require('./loadBalancer/loadBalancer.transformer'),
  AWS_LOAD_BALANCER_MODULE,
  require('./loadBalancer/configure/createLoadBalancer.controller'),
  require('./instance/details/instance.details.controller'),
  AWS_SECURITY_GROUP_MODULE,
  SUBNET_RENDERER,
  VPC_MODULE,
  require('./image/image.reader'),
  require('./cache/cacheConfigurer.service'),
  require('./search/searchResultFormatter'),
]).config((cloudProviderRegistryProvider: CloudProviderRegistry) => {
  cloudProviderRegistryProvider.registerProvider('aws', {
    name: 'Amazon',
    logo: {
      path: require('./logo/amazon.logo.svg'),
    },
    cache: {
      configurer: 'awsCacheConfigurer',
    },
    image: {
      reader: 'awsImageReader',
    },
    serverGroup: {
      transformer: 'awsServerGroupTransformer',
      detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
      detailsController: 'awsServerGroupDetailsCtrl',
      cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
      cloneServerGroupController: 'awsCloneServerGroupCtrl',
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
      createLoadBalancerTemplateUrl: require('./loadBalancer/configure/createLoadBalancer.html'),
      createLoadBalancerController: 'awsCreateLoadBalancerCtrl',
      editLoadBalancerTemplateUrl: require('./loadBalancer/configure/editLoadBalancer.html'),
      targetGroupDetailsTemplateUrl: require('./loadBalancer/details/targetGroupDetails.html'),
      targetGroupDetailsController: 'awsTargetGroupDetailsCtrl',
      ClusterContainer: AmazonLoadBalancerClusterContainer,
      LoadBalancersTag: AmazonLoadBalancersTag
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
