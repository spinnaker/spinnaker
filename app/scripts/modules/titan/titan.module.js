'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.titan', [
  require('../core/cloudProvider/cloudProvider.registry.js'),
  require('./serverGroup/details/serverGroupDetails.titan.controller.js'),
  require('./serverGroup/configure/ServerGroupCommandBuilder.js'),
  require('./serverGroup/configure/wizard/CloneServerGroup.titan.controller.js'),
  require('./serverGroup/configure/serverGroup.configure.titan.module.js'),
  require('./serverGroup/serverGroup.transformer.js'),
//  require('../pipelines/config/stages/bake/docker/dockerBakeStage.js'),
//  require('../pipelines/config/stages/bake/titan/titanBakeStage.js'),
//  require('../pipelines/config/stages/destroyAsg/titan/titanDestroyAsgStage.js'),
//  require('../pipelines/config/stages/resizeAsg/titan/titanResizeAsgStage.js'),
  require('./instance/details/instance.details.controller.js'),
])
  .config(function(cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('titan', {
      logo: {
        path: require('./logo_titan.png')
      },
      serverGroup: {
        transformer: 'titanServerGroupTransformer',
        detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
        detailsController: 'titanServerGroupDetailsCtrl',
        cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
        cloneServerGroupController: 'titanCloneServerGroupCtrl',
        commandBuilder: 'titanServerGroupCommandBuilder',
        configurationService: 'titanServerGroupConfigurationService',
      },
      instance: {
        detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
        detailsController: 'titanInstanceDetailsCtrl'
      }
    });
  }).name;

