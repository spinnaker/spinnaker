'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.trigger.docker', [
    require('core/config/settings.js'),
    require('docker/image/image.reader.js'),
    require('./dockerTriggerOptions.directive.js'),
    require('docker/image/dockerImageAndTagSelector.component.js')
  ])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerTrigger({
      label: 'Docker Registry',
      description: 'Executes the pipeline on an image update',
      key: 'docker',
      controller: 'DockerTriggerCtrl as ctrl',
      controllerAs: 'vm',
      templateUrl: require('./dockerTrigger.html'),
      popoverLabelUrl: require('./dockerPopoverLabel.html'),
      manualExecutionHandler: 'dockerTriggerExecutionHandler',
      validators: [
        { type: 'requiredField', fieldName: 'account',
          message: '<strong>Registry</strong> is a required field for Docker Registry triggers.'},
        { type: 'requiredField', fieldName: 'organization',
          message: '<strong>Organization</strong> is a required field for Docker Registry triggers.' },
        { type: 'requiredField', fieldName: 'repository',
          message: '<strong>Image</strong> is a required field for Docker Registry triggers.'},
      ],
    });
  })
  .factory('dockerTriggerExecutionHandler', function ($q) {
    return {
      formatLabel: (trigger) => {
        return $q.when(`(Docker Registry) ${trigger.account ? trigger.account + ':' : ''} ${trigger.repository || ''}`);
      },
      selectorTemplate: require('./selectorTemplate.html'),
    };
  })
  .controller('DockerTriggerCtrl', function (trigger) {
    this.trigger = trigger;
  });
