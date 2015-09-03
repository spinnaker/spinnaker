'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce', [
  require('../serverGroups/details/gce/serverGroupDetails.gce.controller.js'),
  require('../serverGroups/configure/gce/ServerGroupCommandBuilder.js'),
  require('../serverGroups/configure/gce/wizard/CloneServerGroupCtrl.js'),
  require('../serverGroups/configure/gce/serverGroup.configure.gce.module.js'),
  require('../providerSelection/provider.image.service.provider.js'),
  require('../pipelines/config/stages/bake/gce/gceBakeStage.js'),
  require('../pipelines/config/stages/resizeAsg/gce/gceResizeAsgStage.js'),
])
  .config(function(providerImageServiceProvider) {
    providerImageServiceProvider.registerImage({
      provider: 'gce',
      key: 'logo',
      path: require('../../../images/providers/logo_gce.png')
    });
  }).name;

