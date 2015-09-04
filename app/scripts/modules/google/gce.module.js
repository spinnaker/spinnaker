'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce', [
  require('../core/cloudProvider/cloudProvider.registry.js'),
  require('./serverGroup/details/serverGroupDetails.gce.controller.js'),
  require('./serverGroup/configure/ServerGroupCommandBuilder.js'),
  require('./serverGroup/configure/wizard/CloneServerGroupCtrl.js'),
  require('./serverGroup/configure/serverGroup.configure.gce.module.js'),
  require('../providerSelection/provider.image.service.provider.js'),
  require('../pipelines/config/stages/bake/gce/gceBakeStage.js'),
  require('../pipelines/config/stages/resizeAsg/gce/gceResizeAsgStage.js'),
])
  .config(function(providerImageServiceProvider, cloudProviderRegistryProvider) {
    providerImageServiceProvider.registerImage({
      provider: 'gce',
      key: 'logo',
      path: require('./logo_gce.png')
    });
    cloudProviderRegistryProvider.registerProvider('gce', {
      serverGroup: {
        detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
        detailsController: 'gceServerGroupDetailsCtrl',
        cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
        cloneServerGroupController: 'gceCloneServerGroupCtrl',
      }
    });
  }).name;

