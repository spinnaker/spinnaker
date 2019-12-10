'use strict';

import _ from 'lodash';

import { Registry } from '@spinnaker/core';

import { KUBERNETES_IMAGE_ID_FILTER } from 'kubernetes/v1/presentation/imageId.filter';
import { KUBERNETES_V1_CONTAINER_COMMANDS_COMPONENT } from 'kubernetes/v1/container/commands.component';
import { KUBERNETES_V1_CONTAINER_ARGUMENTS_COMPONENT } from 'kubernetes/v1/container/arguments.component';
import { KUBERNETES_V1_CONTAINER_ENVIRONMENTVARIABLES_COMPONENT } from 'kubernetes/v1/container/environmentVariables.component';
import { KUBERNETES_V1_CONTAINER_VOLUMES_COMPONENT } from 'kubernetes/v1/container/volumes.component';
import { KUBERNETES_V1_IMAGE_IMAGE_READER } from 'kubernetes/v1/image/image.reader';
import { KUBERNETES_V1_PIPELINE_STAGES_RUNJOB_RUNJOBEXECUTIONDETAILS_CONTROLLER } from './runJobExecutionDetails.controller';
import { KUBERNETES_V1_PIPELINE_STAGES_RUNJOB_CONFIGUREJOB_CONTROLLER } from './configureJob.controller';

import * as angular from 'angular';

export const KUBERNETES_V1_PIPELINE_STAGES_RUNJOB_RUNJOBSTAGE = 'spinnaker.kubernetes.pipeline.stage.runJobStage';
export const name = KUBERNETES_V1_PIPELINE_STAGES_RUNJOB_RUNJOBSTAGE; // for backwards compatibility
angular
  .module(KUBERNETES_V1_PIPELINE_STAGES_RUNJOB_RUNJOBSTAGE, [
    KUBERNETES_V1_CONTAINER_COMMANDS_COMPONENT,
    KUBERNETES_V1_CONTAINER_ARGUMENTS_COMPONENT,
    KUBERNETES_V1_CONTAINER_ENVIRONMENTVARIABLES_COMPONENT,
    KUBERNETES_V1_CONTAINER_VOLUMES_COMPONENT,
    KUBERNETES_V1_IMAGE_IMAGE_READER,
    KUBERNETES_V1_PIPELINE_STAGES_RUNJOB_RUNJOBEXECUTIONDETAILS_CONTROLLER,
    KUBERNETES_V1_PIPELINE_STAGES_RUNJOB_CONFIGUREJOB_CONTROLLER,
    KUBERNETES_IMAGE_ID_FILTER,
  ])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'runJob',
      cloudProvider: 'kubernetes',
      templateUrl: require('./runJobStage.html'),
      executionDetailsUrl: require('./runJobExecutionDetails.html'),
      producesArtifacts: true,
      supportsCustomTimeout: true,
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
