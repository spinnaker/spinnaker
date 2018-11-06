import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import './helpContents/oracleHelpContents';
import { ORACLE_LOAD_BALANCER_TRANSFORMER } from 'oracle/loadBalancer/loadBalancer.transformer';
import { ORACLE_LOAD_BALANCER_CREATE_CONTROLLER } from 'oracle/loadBalancer/configure/createLoadBalancer.controller';
import { ORACLE_LOAD_BALANCER_DETAIL_CONTROLLER } from 'oracle/loadBalancer/details/loadBalancerDetail.controller';

const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const ORACLE_MODULE = 'spinnaker.oracle';
module(ORACLE_MODULE, [
  // Cache
  require('./cache/cacheConfigurer.service.js').name,
  // Pipeline
  require('./pipeline/stages/bake/ociBakeStage.js').name,
  require('./pipeline/stages/destroyAsg/destroyAsgStage.js').name,
  require('./pipeline/stages/disableAsg/disableAsgStage.js').name,
  require('./pipeline/stages/findAmi/findAmiStage.js').name,
  require('./pipeline/stages/findImageFromTags/oracleFindImageFromTagsStage.js').name,
  require('./pipeline/stages/resizeAsg/resizeAsgStage.js').name,
  require('./pipeline/stages/scaleDownCluster/scaleDownClusterStage.js').name,
  require('./pipeline/stages/shrinkCluster/shrinkClusterStage.js').name,

  // Load Balancers
  ORACLE_LOAD_BALANCER_TRANSFORMER,
  ORACLE_LOAD_BALANCER_DETAIL_CONTROLLER,
  ORACLE_LOAD_BALANCER_CREATE_CONTROLLER,

  // Server Groups
  require('./serverGroup/serverGroup.transformer.js').name,
  require('./serverGroup/configure/serverGroup.configure.module.js').name,
  require('./serverGroup/details/serverGroupDetails.controller.js').name,
  require('./serverGroup/configure/serverGroupCommandBuilder.service.js').name,
  require('./serverGroup/configure/wizard/cloneServerGroup.controller.js').name,
  // Images
  require('./image/image.reader.js').name,
  // Instances
  require('./instance/details/instance.details.controller.js').name,
  // Firewalls
  require('./securityGroup/securityGroup.reader.js').name,
  require('./securityGroup/securityGroup.transformer.js').name,
  require('./securityGroup/configure/createSecurityGroup.controller.js').name,
]).config(function() {
  CloudProviderRegistry.registerProvider('oracle', {
    name: 'Oracle',
    cache: {
      configurer: 'oracleCacheConfigurer',
    },
    image: {
      reader: 'oracleImageReader',
    },
    loadBalancer: {
      transformer: 'oracleLoadBalancerTransformer',
      detailsTemplateUrl: require('./loadBalancer/details/loadBalancerDetail.html'),
      detailsController: 'oracleLoadBalancerDetailCtrl',
      createLoadBalancerTemplateUrl: require('./loadBalancer/configure/createLoadBalancer.html'),
      createLoadBalancerController: 'oracleCreateLoadBalancerCtrl',
    },
    serverGroup: {
      transformer: 'oracleServerGroupTransformer',
      detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
      detailsController: 'oracleServerGroupDetailsCtrl',
      commandBuilder: 'oracleServerGroupCommandBuilder',
      cloneServerGroupController: 'oracleCloneServerGroupCtrl',
      cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
      configurationService: 'oracleServerGroupConfigurationService',
    },
    instance: {
      detailsController: 'oracleInstanceDetailsCtrl',
      detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
    },
    securityGroup: {
      reader: 'oracleSecurityGroupReader',
      transformer: 'oracleSecurityGroupTransformer',
      createSecurityGroupTemplateUrl: require('./securityGroup/configure/createSecurityGroup.html'),
      createSecurityGroupController: 'oracleCreateSecurityGroupCtrl',
    },
  });
});

DeploymentStrategyRegistry.registerProvider('oracle', []);
