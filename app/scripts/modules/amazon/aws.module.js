'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws', [
  require('../serverGroups/details/aws/serverGroup.details.module.js'),
  require('../serverGroups/configure/aws/wizard/CloneServerGroup.aws.controller.js'),
  require('../serverGroups/configure/aws/serverGroup.configure.aws.module.js'),
  require('../providerSelection/provider.image.service.provider.js'),
  require('../pipelines/config/stages/bake/aws/awsBakeStage.js'),
  require('../pipelines/config/stages/resizeAsg/aws/awsResizeAsgStage.js'),
])
  .config(function(providerImageServiceProvider) {
    providerImageServiceProvider.registerImage({
      provider: 'aws',
      key: 'logo',
      path: require('../../../images/providers/logo_aws.png')
    });
  }).name;
