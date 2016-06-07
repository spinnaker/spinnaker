'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.templateOverride.templateOverrides', [
    require('../../core/overrideRegistry/override.registry.js'),
    require('../../core/config/settings.js'),
  ])
  .run(function(overrideRegistry, settings) {
    if (settings.feature && settings.feature.netflixMode) {
      let templates = [
        { key: 'applicationConfigView', value: require('../application/applicationConfig.html') },
        { key: 'applicationAttributesDirective', value: require('../application/applicationAttributes.directive.html') },
        { key: 'createApplicationModal', value: require('../application/createApplication.modal.html') },
        { key: 'editApplicationModal', value: require('../application/editApplication.modal.html') },
        { key: 'applicationNavHeader', value: require('../application/applicationNav.html') },
        { key: 'pipelineConfigActions', value: require('./pipelineConfigActions.html') },
        { key: 'spinnakerHeader', value: require('./spinnakerHeader.html') },
        { key: 'aws.serverGroup.securityGroups', value: require('../serverGroup/wizard/securityGroups/awsServerGroupSecurityGroups.html') },
        { key: 'aws.serverGroup.capacity', value: require('../serverGroup/wizard/capacity/awsServerGroupCapacity.html') },
        { key: 'aws.serverGroup.advancedSettings', value: require('../serverGroup/wizard/advancedSettings/advancedSettings.html') },
        { key: 'aws.resize.modal', value: require('../serverGroup/resize/awsResizeServerGroup.html') },
      ];
      templates.forEach((template) => overrideRegistry.overrideTemplate(template.key, template.value));

      let controllers = [
        { key: 'CreateApplicationModalCtrl', value: 'netflixCreateApplicationModalCtrl' },
        { key: 'EditApplicationController', value: 'netflixEditApplicationController' },
      ];
      controllers.forEach((ctrl) => overrideRegistry.overrideController(ctrl.key, ctrl.value));
    }
  });
