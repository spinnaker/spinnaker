
'use strict';

const angular = require('angular');

import { SERVICE_ACCOUNT_SERVICE, SETTINGS } from '@spinnaker/core';

import { DOCKER_IMAGE_READER } from 'docker/image/docker.image.reader.service';
import { DOCKER_IMAGE_AND_TAG_SELECTOR_COMPONENT } from 'docker/image/dockerImageAndTagSelector.component';

module.exports = angular.module('spinnaker.core.pipeline.trigger.docker', [
    SERVICE_ACCOUNT_SERVICE,
    DOCKER_IMAGE_READER,
    require('./dockerTriggerOptions.directive.js'),
    DOCKER_IMAGE_AND_TAG_SELECTOR_COMPONENT
  ])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerTrigger({
      label: 'Docker Registry',
      description: 'Executes the pipeline on an image update',
      key: 'docker',
      controller: 'DockerTriggerCtrl as ctrl',
      controllerAs: 'vm',
      templateUrl: require('./dockerTrigger.html'),
      manualExecutionHandler: 'dockerTriggerExecutionHandler',
      validators: [
        { type: 'requiredField', fieldName: 'account',
          message: '<strong>Registry</strong> is a required field for Docker Registry triggers.'},
        { type: 'requiredField', fieldName: 'repository',
          message: '<strong>Image</strong> is a required field for Docker Registry triggers.'},
        { type: 'serviceAccountAccess', preventSave: true,
          message: `You do not have access to the service account configured in this pipeline's Docker Registry trigger.
                    You will not be able to save your edits to this pipeline.`}
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
  .controller('DockerTriggerCtrl', function (trigger, serviceAccountService) {
    this.trigger = trigger;
    this.fiatEnabled = SETTINGS.feature.fiatEnabled;

    serviceAccountService.getServiceAccounts().then(accounts => {
      this.serviceAccounts = accounts || [];
    });

    this.onChange = (changes) => {
      this.trigger.registry = changes.registry;
    };
  });
