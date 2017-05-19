import { module } from 'angular';

import { CLOUD_PROVIDER_REGISTRY } from '@spinnaker/core';

import { AMAZON_APPLICATION_NAME_VALIDATOR } from './validation/applicationName.validator';

import { AMAZON_HELP } from './help/amazon.help';

import './logo/aws.logo.less';
import { CloudProviderRegistry } from 'core/cloudProvider';

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
  require('./serverGroup/details/serverGroup.details.module'),
  require('./serverGroup/serverGroup.transformer'),
  require('./serverGroup/configure/wizard/CloneServerGroup.aws.controller'),
  require('./serverGroup/configure/serverGroup.configure.aws.module'),
  require('./instance/awsInstanceType.service'),
  require('./loadBalancer/loadBalancer.transformer'),
  require('./loadBalancer/details/loadBalancerDetail.controller'),
  require('./loadBalancer/configure/createLoadBalancer.controller'),
  require('./instance/details/instance.details.controller'),
  require('./securityGroup/details/securityGroupDetail.controller'),
  require('./securityGroup/configure/CreateSecurityGroupCtrl'),
  require('./securityGroup/configure/EditSecurityGroupCtrl'),
  require('./securityGroup/securityGroup.transformer'),
  require('./securityGroup/securityGroup.reader'),
  require('./subnet/subnet.renderer'),
  require('./vpc/vpc.module'),
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
      detailsTemplateUrl: require('./loadBalancer/details/loadBalancerDetail.html'),
      detailsController: 'awsLoadBalancerDetailsCtrl',
      createLoadBalancerTemplateUrl: require('./loadBalancer/configure/createLoadBalancer.html'),
      createLoadBalancerController: 'awsCreateLoadBalancerCtrl',
      editLoadBalancerTemplateUrl: require('./loadBalancer/configure/editLoadBalancer.html'),
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
