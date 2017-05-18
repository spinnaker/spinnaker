'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.pipeline.stage.runJobStage', [
  require('kubernetes/container/commands.component.js'),
  require('kubernetes/container/arguments.component.js'),
  require('kubernetes/container/environmentVariables.component.js'),
  require('kubernetes/container/volumes.component.js'),
  require('./runJobExecutionDetails.controller.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'runJob',
      cloudProvider: 'kubernetes',
      templateUrl: require('./runJobStage.html'),
      executionDetailsUrl: require('./runJobExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'account' },
        { type: 'requiredField', fieldName: 'namespace' },
        { type: 'custom', validate: (_pipeline, stage) => {
          let response = null;

          if (stage.container.imageDescription.fromTrigger === false && !stage.container.imageDescription.tag) {
            response = '<strong>Tag</strong> is a required field for Run Job stages.';
          } else if (stage.container.imageDescription.fromTrigger === true && !stage.container.imageDescription.registry) {
            response = '<strong>Trigger</strong> is a required field for Run Job stages.';
          }

          return response;
        }},
      ]
    });
  }).controller('kubernetesRunJobStageCtrl', function($scope, accountService) {

    this.stage = $scope.stage;
    this.pipeline = $scope.pipeline;
    this.container = null;

    const buildImageDescriptor = (image) => {
      let descriptor = `${image.account}/${image.repository}`;
      if (image.tag) {
        descriptor += `:${image.tag}`;
      }
      return descriptor;
    };

    if (!_.has(this.stage, 'container.name')) {
      _.set(this.stage, 'container.name', Date.now().toString());
    }

    if (!_.has(this.stage, 'container.imageDescription.fromTrigger')) {
      _.set(this.stage, 'container.imageDescription.fromTrigger', false);
    }


    if (this.stage.container.imageDescription.fromTrigger === true) {
      this.container = buildImageDescriptor(this.stage.container.imageDescription);
    }

    accountService.getUniqueAttributeForAllAccounts('kubernetes', 'namespaces')
      .then((namespaces) => {
        this.namespaces = namespaces;
      });

    accountService.listAccounts('kubernetes')
      .then((accounts) => {
        this.accounts = accounts;
      });

    this.stage.cloudProvider = 'kubernetes';
    this.stage.application = $scope.application.name;

    if (!this.stage.credentials && $scope.application.defaultCredentials.kubernetes) {
      this.stage.credentials = $scope.application.defaultCredentials.kubernetes;
    }

    this.onChange = (changes) => {
      this.stage.container.imageDescription.registry = changes.registry;
    };

    this.triggerImages = () => {

      if (this.pipeline.triggers.length <= 0) {
        return [];
      }

      return this.pipeline.triggers.filter((trigger) => {
        return trigger.type === 'docker' && trigger.enabled === true;
      }).map((trigger) => {
        return buildImageDescriptor(trigger);
      });
    };

    this.hasDockerPipelineTriggers = () => {
      return this.triggerImages().length > 0;
    };

    this.updateContainerImage = () => {

      const trigger = this.pipeline.triggers.find((trigger) => buildImageDescriptor(trigger) === this.container);

      if (trigger) {
        this.stage.container.imageDescription.account = trigger.account;
        this.stage.container.imageDescription.organization = trigger.organization;
        this.stage.container.imageDescription.repository = trigger.repository;
        this.stage.container.imageDescription.registry = trigger.registry;
        if (trigger.tag) {
          this.stage.container.imageDescription.tag = trigger.tag;
        } else {
          this.stage.container.imageDescription.tag = null;
        }
      }
    };

  });
