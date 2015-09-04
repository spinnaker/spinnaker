'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws', [
  require('../core/cloudProvider/cloudProvider.registry.js'),
  require('./serverGroup/details/serverGroup.details.module.js'),
  require('./serverGroup/configure/wizard/CloneServerGroup.aws.controller.js'),
  require('./serverGroup/configure/serverGroup.configure.aws.module.js'),
  require('../providerSelection/provider.image.service.provider.js'),
  require('../pipelines/config/stages/bake/aws/awsBakeStage.js'),
  require('../pipelines/config/stages/resizeAsg/aws/awsResizeAsgStage.js'),
])
  .config(function(providerImageServiceProvider, cloudProviderRegistryProvider) {
    providerImageServiceProvider.registerImage({
      provider: 'aws',
      key: 'logo',
      path: require('./logo_aws.png')
    });
    cloudProviderRegistryProvider.registerProvider('aws', {
      serverGroup: {
        detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
        detailsController: 'awsServerGroupDetailsCtrl',
        cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
        cloneServerGroupController: 'awsCloneServerGroupCtrl',
      }
    });
  }).name;
