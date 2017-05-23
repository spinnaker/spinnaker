'use strict';

const angular = require('angular');

import { CLOUD_PROVIDER_REGISTRY } from '@spinnaker/core';

import { ORACLE_HELP_CONTENTS_REGISTRY } from './helpContents/oracleHelpContents';

let templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular.module('spinnaker.oraclebmcs', [
  CLOUD_PROVIDER_REGISTRY,
  ORACLE_HELP_CONTENTS_REGISTRY,
  //Cache
  require('./cache/cacheConfigurer.service.js'),
  // Pipeline
  require('./pipeline/stages/bake/bakeStage.js'),
  require('./pipeline/stages/destroyAsg/destroyAsgStage.js'),
  require('./pipeline/stages/resizeAsg/resizeAsgStage.js'),
  require('./pipeline/stages/scaleDownCluster/scaleDownClusterStage.js'),
  require('./pipeline/stages/shrinkCluster/shrinkClusterStage.js'),
  require('./pipeline/stages/findAmi/findAmiStage.js'),
  // Server Groups
  require('./serverGroup/serverGroup.transformer.js'),
  require('./serverGroup/configure/serverGroup.configure.module.js'),
  require('./serverGroup/details/serverGroupDetails.controller.js'),
  require('./serverGroup/configure/serverGroupCommandBuilder.service.js'),
  require('./serverGroup/configure/wizard/cloneServerGroup.controller.js'),
  require('./serverGroup/configure/wizard/templateSelection/deployInitializer.controller.js'),
  // Images
  require('./image/image.reader.js'),
  // Instances
  require('./instance/details/instance.details.controller.js'),
  // Security Groups
  require('./securityGroup/securityGroup.reader.js'),
  require('./securityGroup/securityGroup.transformer.js'),
  require('./securityGroup/configure/createSecurityGroup.controller.js'),
])
  .config(function (cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('oraclebmcs', {
      name: 'Oracle',
      cache: {
        configurer: 'oraclebmcsCacheConfigurer',
      },
      image: {
        reader: 'oraclebmcsImageReader',
      },
      loadBalancer: {
      },
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
        detailsTemplateUrl: require('./instance/details/instanceDetails.html')
      },
      securityGroup: {
        reader: 'oraclebmcsSecurityGroupReader',
        transformer: 'oraclebmcsSecurityGroupTransformer',
        createSecurityGroupTemplateUrl: require('./securityGroup/configure/createSecurityGroup.html'),
        createSecurityGroupController: 'oraclebmcsCreateSecurityGroupCtrl'
      }
    });
  });
