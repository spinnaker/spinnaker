'use strict';

import { module } from 'angular';
import { pickBy } from 'lodash';

import { PipelineParametersExecutionDetails } from './PipelineParametersExecutionDetails';
import { PipelineStageExecutionDetails } from './PipelineStageExecutionDetails';
import { ApplicationReader } from '../../../../application/service/ApplicationReader';
import { ExecutionDetailsTasks } from '../common';
import { Registry } from '../../../../registry';
import { PipelineConfigService } from '../../services/PipelineConfigService';
import { PipelineTemplateReader, PipelineTemplateV2Service } from '../../templates';

export const CORE_PIPELINE_CONFIG_STAGES_PIPELINE_PIPELINESTAGE = 'spinnaker.core.pipeline.stage.pipelineStage';
export const name = CORE_PIPELINE_CONFIG_STAGES_PIPELINE_PIPELINESTAGE; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_STAGES_PIPELINE_PIPELINESTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      label: 'Pipeline',
      description: 'Runs a pipeline',
      key: 'pipeline',
      restartable: true,
      controller: 'pipelineStageCtrl',
      controllerAs: 'pipelineStageCtrl',
      producesArtifacts: true,
      templateUrl: require('./pipelineStage.html'),
      executionDetailsSections: [
        PipelineStageExecutionDetails,
        PipelineParametersExecutionDetails,
        ExecutionDetailsTasks,
      ],
      supportsCustomTimeout: true,
      validators: [{ type: 'requiredField', fieldName: 'pipeline' }],
    });
  })
  .controller('pipelineStageCtrl', [
    '$scope',
    'stage',
    function ($scope, stage) {
      $scope.stage = stage;
      $scope.stage.failPipeline = $scope.stage.failPipeline === undefined ? true : $scope.stage.failPipeline;
      $scope.stage.waitForCompletion =
        $scope.stage.waitForCompletion === undefined ? true : $scope.stage.waitForCompletion;

      if (!$scope.stage.application) {
        $scope.stage.application = $scope.application.name;
      }

      $scope.viewState = {
        mastersLoaded: false,
        mastersRefreshing: false,
        mastersLastRefreshed: null,
        pipelinesLoaded: false,
        jobsRefreshing: false,
        jobsLastRefreshed: null,
        infiniteScroll: {
          numToAdd: 20,
          currentItems: 20,
        },
      };

      this.hasSpel = function (string) {
        return string && string.includes('${');
      };
      this.addMoreItems = function () {
        $scope.viewState.infiniteScroll.currentItems += $scope.viewState.infiniteScroll.numToAdd;
      };

      ApplicationReader.listApplications().then(function (applications) {
        $scope.applications = _.map(applications, 'name').sort();
        initializeMasters();
      });

      function initializeMasters() {
        if ($scope.stage.application && !$scope.stage.application.includes('${')) {
          PipelineConfigService.getPipelinesForApplication($scope.stage.application).then(function (pipelines) {
            $scope.pipelines = _.filter(pipelines, (pipeline) => pipeline.id !== $scope.pipeline.id);
            const pipelineId = $scope.stage.pipeline;
            const isFound = _.find(pipelines, (pipeline) => pipeline.id === pipelineId);
            if (!isFound && pipelineId && !pipelineId.includes('${')) {
              $scope.stage.pipeline = null;
            }
            $scope.viewState.pipelinesLoaded = true;
            updatePipelineConfig();
          });
        }
      }

      function updatePipelineConfig() {
        const pipeline = $scope.stage && $scope.stage.pipeline;
        if (pipeline && pipeline.includes('${')) {
          return;
        }

        if ($scope.stage && $scope.stage.application && pipeline) {
          const config = _.find($scope.pipelines, (pipeline) => pipeline.id === $scope.stage.pipeline);
          if (config && PipelineTemplateV2Service.isV2PipelineConfig(config)) {
            PipelineTemplateReader.getPipelinePlan(config)
              .then((plan) => applyPipelineConfigParameters(plan))
              .catch(() => clearParams());
          } else {
            applyPipelineConfigParameters(config);
          }
        } else {
          clearParams();
        }
      }

      function applyPipelineConfigParameters(config) {
        if (config && config.parameterConfig) {
          if (!$scope.stage.pipelineParameters) {
            $scope.stage.pipelineParameters = {};
          }
          $scope.pipelineParameters = config.parameterConfig;
          $scope.pipelineParameters.forEach((parameterConfig) => {
            if (
              parameterConfig.default &&
              parameterConfig.options &&
              !parameterConfig.options.some((option) => option.value === parameterConfig.default)
            ) {
              parameterConfig.options.unshift({ value: parameterConfig.default });
            }
          });
          $scope.userSuppliedParameters = $scope.stage.pipelineParameters;

          if ($scope.pipelineParameters) {
            const acceptedPipelineParams = $scope.pipelineParameters.map((param) => param.name);
            $scope.invalidParameters = pickBy(
              $scope.userSuppliedParameters,
              (value, name) => !acceptedPipelineParams.includes(name),
            );
          }

          $scope.hasInvalidParameters = () => Object.keys($scope.invalidParameters || {}).length;
          $scope.useDefaultParameters = {};
          _.each($scope.pipelineParameters, function (property) {
            if (!(property.name in $scope.stage.pipelineParameters) && property.default !== null) {
              $scope.useDefaultParameters[property.name] = true;
            }
          });
        } else {
          clearParams();
        }
      }

      function clearParams() {
        $scope.pipelineParameters = {};
        $scope.useDefaultParameters = {};
        $scope.userSuppliedParameters = {};
      }

      $scope.useDefaultParameters = {};
      $scope.userSuppliedParameters = {};

      this.updateParam = function (parameter) {
        if ($scope.useDefaultParameters[parameter] === true) {
          delete $scope.userSuppliedParameters[parameter];
          delete $scope.stage.pipelineParameters[parameter];
        } else if ($scope.userSuppliedParameters[parameter]) {
          $scope.stage.pipelineParameters[parameter] = $scope.userSuppliedParameters[parameter] || '';
        }
      };

      this.removeInvalidParameters = function () {
        Object.keys($scope.invalidParameters).forEach((param) => {
          if ($scope.stage.pipelineParameters[param] !== 'undefined') {
            delete $scope.stage.pipelineParameters[param];
          }
        });
        $scope.invalidParameters = [];
      };

      $scope.$watch('stage.application', initializeMasters);
      $scope.$watch('stage.pipeline', updatePipelineConfig);
    },
  ]);
