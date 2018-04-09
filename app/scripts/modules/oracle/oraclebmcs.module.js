'use strict';

const angular = require('angular');

import { CLOUD_PROVIDER_REGISTRY, DeploymentStrategyRegistry } from '@spinnaker/core';

import { ORACLE_HELP_CONTENTS_REGISTRY } from './helpContents/oracleHelpContents';

let templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular
  .module('spinnaker.oraclebmcs', [
    CLOUD_PROVIDER_REGISTRY,
    ORACLE_HELP_CONTENTS_REGISTRY,
    //Cache
    require('./cache/cacheConfigurer.service.js').name,
    // Pipeline
    require('./pipeline/stages/bake/bakeStage.js').name,
    require('./pipeline/stages/destroyAsg/destroyAsgStage.js').name,
    require('./pipeline/stages/disableAsg/disableAsgStage.js').name,
    require('./pipeline/stages/findAmi/findAmiStage.js').name,
    require('./pipeline/stages/resizeAsg/resizeAsgStage.js').name,
    require('./pipeline/stages/scaleDownCluster/scaleDownClusterStage.js').name,
    require('./pipeline/stages/shrinkCluster/shrinkClusterStage.js').name,
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
    // Security Groups
    require('./securityGroup/securityGroup.reader.js').name,
    require('./securityGroup/securityGroup.transformer.js').name,
    require('./securityGroup/configure/createSecurityGroup.controller.js').name,
  ])
  .config(function(cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('oraclebmcs', {
      name: 'Oracle',
      cache: {
        configurer: 'oraclebmcsCacheConfigurer',
      },
      image: {
        reader: 'oraclebmcsImageReader',
      },
      loadBalancer: {},
      serverGroup: {
        transformer: 'oraclebmcsServerGroupTransformer',
        detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
        detailsController: 'oraclebmcsServerGroupDetailsCtrl',
        commandBuilder: 'oraclebmcsServerGroupCommandBuilder',
        cloneServerGroupController: 'oraclebmcsCloneServerGroupCtrl',
        cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
        configurationService: 'oraclebmcsServerGroupConfigurationService',
      },
      instance: {
        detailsController: 'oraclebmcsInstanceDetailsCtrl',
        detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
      },
      securityGroup: {
        reader: 'oraclebmcsSecurityGroupReader',
        transformer: 'oraclebmcsSecurityGroupTransformer',
        createSecurityGroupTemplateUrl: require('./securityGroup/configure/createSecurityGroup.html'),
        createSecurityGroupController: 'oraclebmcsCreateSecurityGroupCtrl',
      },
    });
  });

DeploymentStrategyRegistry.registerProvider('oraclebmcs', []);
