'use strict';

let angular = require('angular');

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular
  .module('spinnaker.netflix', [
    require('./whatsNew/whatsNew.directive.js'),
    require('./blesk/blesk.module.js'),
    require('./fastProperties/fastProperties.module.js'),
    require('./alert/alertHandler.js'),
    require('./feedback/feedback.module.js'),
    require('./instance/aws/netflixAwsInstanceDetails.controller.js'),
    require('./instance/titus/netflixTitusInstanceDetails.controller.js'),
    require('./pipeline/stage/canary/canaryStage.module.js'),
    require('./pipeline/stage/acaTask/acaTaskStage.module'),
    require('./pipeline/stage/properties'),
    require('./pipeline/stage/quickPatchAsg/quickPatchAsgStage.module.js'),
    require('./pipeline/stage/quickPatchAsg/bulkQuickPatchStage/bulkQuickPatchStage.module.js'),
    require('./pipeline/config/properties'),

    require('./canary'),
    require('./templateOverride/templateOverrides.module.js'),
    require('./migrator/pipeline/pipeline.migrator.directive.js'),
    require('./serverGroup/wizard/serverGroupCommandConfigurer.service.js'),
    require('./serverGroup/diff/securityGroupDiff.directive.js'),
    require('./serverGroup/networking/networking.module.js'),
    require('./report/reservationReport.directive.js'),

    require('./application/netflixCreateApplicationModal.controller.js'),
    require('./application/netflixEditApplicationModal.controller.js'),
    require('./help/netflixHelpContents.registry.js'),

    require('./chaosMonkey/chaosMonkeyConfig.directive.js'),

    require('../core/config/settings.js'),

    require('./tableau/states'),
  ])
  .run(function(cloudProviderRegistry, settings) {
    if (settings.feature && settings.feature.netflixMode) {
      cloudProviderRegistry.overrideValue(
        'aws',
        'instance.detailsTemplateUrl',
        require('./instance/aws/instanceDetails.html')
      );
      cloudProviderRegistry.overrideValue(
        'aws',
        'instance.detailsController',
        'netflixAwsInstanceDetailsCtrl'
      );
      cloudProviderRegistry.overrideValue(
        'aws',
        'serverGroup.detailsTemplateUrl',
        require('./serverGroup/awsServerGroupDetails.html')
      );
      cloudProviderRegistry.overrideValue(
        'titus',
        'instance.detailsTemplateUrl',
        require('./instance/titus/instanceDetails.html')
      );
      cloudProviderRegistry.overrideValue(
        'titus',
        'instance.detailsController',
        'netflixTitusInstanceDetailsCtrl'
      );
    }
  });
