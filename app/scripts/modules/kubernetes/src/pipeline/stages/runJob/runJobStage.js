'use strict';

import _ from 'lodash';
import { PIPELINE_CONFIG_SERVICE } from '@spinnaker/core';
import { KUBERNETES_IMAGE_ID_FILTER } from 'kubernetes/presentation/imageId.filter';

const angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.pipeline.stage.runJobStage', [
  require('kubernetes/container/commands.component.js').name,
  require('kubernetes/container/arguments.component.js').name,
  require('kubernetes/container/environmentVariables.component.js').name,
  require('kubernetes/container/volumes.component.js').name,
  require('kubernetes/image/image.reader.js').name,
  require('./runJobExecutionDetails.controller.js').name,
  require('./configureJob.controller.js').name,
  PIPELINE_CONFIG_SERVICE,
  KUBERNETES_IMAGE_ID_FILTER,
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'runJob',
      cloudProvider: 'kubernetes',
      templateUrl: require('./runJobStage.html'),
      executionDetailsUrl: require('./runJobExecutionDetails.html'),
      defaultTimeoutMs: 2 * 60 * 60 * 1000, // 2 hours
      validators: [
        { type: 'requiredField', fieldName: 'account' },
        { type: 'requiredField', fieldName: 'namespace' },
      ]
    });
  }).controller('kubernetesRunJobStageCtrl', function($scope, $uibModal) {

    this.stage = $scope.stage;
    this.pipeline = $scope.pipeline;
    this.stage.cloudProvider = 'kubernetes';
    this.stage.application = $scope.application.name;

    this.configureJob = () => {
      return $uibModal.open({
        templateUrl: require('./configureJob.html'),
        controller: 'kubernetesConfigureJobController as ctrl',
        size: 'lg',
        resolve: {
          stage: () => angular.copy(this.stage),
          pipeline: () => this.pipeline,
          application: () => $scope.application
        }
      }).result.then((stage) => {
        _.extend(this.stage, stage);
      }).catch(() => {});
    };

  });
