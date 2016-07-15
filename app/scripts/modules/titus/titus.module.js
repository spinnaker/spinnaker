'use strict';

let angular = require('angular');

require('./logo/titus.logo.less');

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular.module('spinnaker.titus', [
  require('../core/cloudProvider/cloudProvider.registry.js'),
  require('./serverGroup/details/serverGroupDetails.titus.controller.js'),
  require('./serverGroup/configure/ServerGroupCommandBuilder.js'),
  require('./serverGroup/configure/wizard/CloneServerGroup.titus.controller.js'),
  require('./serverGroup/configure/serverGroup.configure.titus.module.js'),
  require('./serverGroup/serverGroup.transformer.js'),
  require('./instance/details/instance.details.controller.js'),
  require('./validation/applicationName.validator.js'),
  require('../core/pipeline/config/stages/findAmi/titus/titusFindAmiStage.js'),
])
  .config(function(cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('titus', {
      name: 'Titus',
      logo: {
        path: require('./logo/titus.logo.png')
      },
      serverGroup: {
        transformer: 'titusServerGroupTransformer',
        detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
        detailsController: 'titusServerGroupDetailsCtrl',
        cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
        cloneServerGroupController: 'titusCloneServerGroupCtrl',
        commandBuilder: 'titusServerGroupCommandBuilder',
        configurationService: 'titusServerGroupConfigurationService',
      },
      instance: {
        detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
        detailsController: 'titusInstanceDetailsCtrl'
      }
    });
  });

