'use strict';

import _ from 'lodash';

import { Registry } from '@spinnaker/core';

import { KUBERNETES_IMAGE_ID_FILTER } from 'kubernetes/presentation/imageId.filter';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.kubernetes.pipeline.stage.runJobStage', [
    require('kubernetes/container/commands.component').name,
    require('kubernetes/container/arguments.component').name,
    require('kubernetes/container/environmentVariables.component').name,
    require('kubernetes/container/volumes.component').name,
    require('kubernetes/image/image.reader').name,
    require('./runJobExecutionDetails.controller').name,
    require('./configureJob.controller').name,
    KUBERNETES_IMAGE_ID_FILTER,
  ])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'runJob',
      cloudProvider: 'kubernetes',
      templateUrl: require('./runJobStage.html'),
      executionDetailsUrl: require('./runJobExecutionDetails.html'),
      producesArtifacts: true,
      defaultTimeoutMs: 2 * 60 * 60 * 1000, // 2 hours
      validators: [{ type: 'requiredField', fieldName: 'account' }, { type: 'requiredField', fieldName: 'namespace' }],
    });
  })
  .controller('kubernetesRunJobStageCtrl', [
    '$scope',
    '$uibModal',
    function($scope, $uibModal) {
      this.stage = $scope.stage;
      this.pipeline = $scope.pipeline;
      this.stage.cloudProvider = 'kubernetes';
      this.stage.application = $scope.application.name;

      if (this.stage.container && !this.stage.containers) {
        this.stage.containers = [this.stage.container];
        delete this.stage.container;
      }

      this.configureJob = () => {
        return $uibModal
          .open({
            templateUrl: require('./configureJob.html'),
            controller: 'kubernetesConfigureJobController as ctrl',
            size: 'lg',
            resolve: {
              stage: () => angular.copy(this.stage),
              pipeline: () => this.pipeline,
              application: () => $scope.application,
            },
          })
          .result.then(stage => {
            _.extend(this.stage, stage);
          })
          .catch(() => {});
      };
    },
  ]);
