'use strict';

const angular = require('angular');

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {API_SERVICE} from 'core/api/api.service';
import {BASE_EXECUTION_DETAILS_CTRL} from './core/baseExecutionDetails.controller';
import {CONFIRMATION_MODAL_SERVICE} from 'core/confirmationModal/confirmationModal.service';
import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';
import {PIPELINE_CONFIG_SERVICE} from 'core/pipeline/config/services/pipelineConfig.service';
import {PIPELINE_BAKE_STAGE_CHOOSE_OS} from 'core/pipeline/config/stages/bake/bakeStageChooseOs.component';

module.exports = angular.module('spinnaker.core.pipeline.config.stage', [
  ACCOUNT_SERVICE,
  API_SERVICE,
  BASE_EXECUTION_DETAILS_CTRL,
  PIPELINE_CONFIG_PROVIDER,
  PIPELINE_CONFIG_SERVICE,
  require('./overrideTimeout/overrideTimeout.directive.js'),
  require('./overrideFailure/overrideFailure.component.js'),
  require('./optionalStage/optionalStage.directive.js'),
  CONFIRMATION_MODAL_SERVICE,
  PIPELINE_BAKE_STAGE_CHOOSE_OS,
  require('./core/stageConfigField/stageConfigField.directive.js'),
  require('./bake/bakeStage.module')
])
  .directive('pipelineConfigStage', function() {
    return {
      restrict: 'E',
      require: '^pipelineConfigurer',
      scope: {
        viewState: '=',
        application: '=',
        pipeline: '=',
      },
      controller: 'StageConfigCtrl as stageConfigCtrl',
      templateUrl: require('./stage.html'),
      link: function(scope, elem, attrs, pipelineConfigurerCtrl) {
        scope.pipelineConfigurerCtrl = pipelineConfigurerCtrl;
      }
    };
  })
  .controller('StageConfigCtrl', function($scope, $element, $compile, $controller, $templateCache,
                                          pipelineConfigService, pipelineConfig, accountService) {

    var lastStageScope;

    $scope.options = {
      stageTypes: [],
      selectedStageType: null,
    };

    accountService.listProviders($scope.application).then((providers) => {
      $scope.options.stageTypes = pipelineConfig.getConfigurableStageTypes(providers);
      $scope.showProviders = providers.length > 1;
    });

    if ($scope.pipeline.strategy) {
      $scope.options.stageTypes = $scope.options.stageTypes.filter((stageType) => {
            return stageType.strategy || false;
      });
    }

    function getConfig(type) {
      return pipelineConfig.getStageConfig({type: type});
    }

    $scope.groupDependencyOptions = function(stage) {
      var requisiteStageRefIds = $scope.stage.requisiteStageRefIds || [];
      return stage.available ? 'Available' :
        requisiteStageRefIds.includes(stage.refId) ? null : 'Downstream dependencies (unavailable)';
    };

    $scope.updateAvailableDependencyStages = function() {
      if (!$scope.pipeline.parallel) {
        return;
      }
      var availableDependencyStages = pipelineConfigService.getDependencyCandidateStages($scope.pipeline, $scope.stage);
      $scope.options.dependencies = availableDependencyStages.map(function(stage) {
        return {
          name: stage.name,
          refId: stage.refId,
          available: true,
        };
      });

      $scope.pipeline.stages.forEach(function(stage) {
        if (stage !== $scope.stage && !availableDependencyStages.includes(stage)) {
          $scope.options.dependencies.push({
            name: stage.name,
            refId: stage.refId,
          });
        }
      });
    };

    this.selectStageType = (type) => {
      $scope.stage.type = type;
      this.selectStage();
    };

    this.selectStage = function(newVal, oldVal) {
      if ($scope.viewState.stageIndex >= $scope.pipeline.stages.length) {
        $scope.viewState.stageIndex = $scope.pipeline.stages.length - 1;
      }
      $scope.stage = $scope.pipeline.stages[$scope.viewState.stageIndex];

      if (!$scope.stage) {
        return;
      }

      if (!$scope.stage.type) {
        $scope.options.selectedStageType = null;
      } else {
        $scope.options.selectedStageType = $scope.stage.type;
      }

      $scope.updateAvailableDependencyStages();
      var type = $scope.stage.type,
          stageScope = $scope.$new();

      // clear existing contents
      $element.find('.stage-details').html('');
      $scope.description = '';
      if (lastStageScope) {
        lastStageScope.$destroy();
      }
      $scope.extendedDescription = '';
      lastStageScope = stageScope;
      $scope.$on('$destroy', function() {
        stageScope.$destroy();
      });

      if (type) {
        let config = getConfig(type);
        if (config) {
          $scope.canConfigureNotifications = !$scope.pipeline.strategy && !config.disableNotifications;
          $scope.description = config.description;
          $scope.extendedDescription = config.extendedDescription;
          $scope.label = config.label;
          if (config.useBaseProvider || config.provides) {
            config.templateUrl = require('./baseProviderStage/baseProviderStage.html');
            config.controller = 'BaseProviderStageCtrl as baseProviderStageCtrl';
          }
          updateStageName(config, oldVal);
          applyConfigController(config, stageScope);

          let template = $templateCache.get(config.templateUrl);
          let templateBody = $compile(template)(stageScope);
          $element.find('.stage-details').html(templateBody);
        }
      } else {
        $scope.label = null;
        $scope.description = null;
        $scope.extendedDescription = null;
      }
    };

    function applyConfigController(config, stageScope) {
      if (config.controller) {
        var ctrl = config.controller.split(' as ');
        var controller = $controller(ctrl[0], {$scope: stageScope, stage: $scope.stage, viewState: $scope.viewState});
        if (ctrl.length === 2) {
          stageScope[ctrl[1]] = controller;
        }
        if (config.controllerAs) {
          stageScope[config.controllerAs] = controller;
        }
      }
    }

    function updateStageName(config, oldVal) {
      // apply a default name if the type changes and the user has not specified a name
      if (oldVal) {
        var oldConfig = getConfig(oldVal);
        if (oldConfig && $scope.stage.name === oldConfig.label) {
          $scope.stage.name = config.label;
        }
      }
      if (!$scope.stage.name && config.label) {
        $scope.stage.name = config.label;
      }
    }

    $scope.$on('pipeline-reverted', this.selectStage);
    $scope.$on('pipeline-json-edited', this.selectStage);
    $scope.$watch('stage.type', this.selectStage);
    $scope.$watch('viewState.stageIndex', this.selectStage);
    $scope.$watch('stage.refId', this.selectStage);
  })
  .controller('RestartStageCtrl', function($scope, $stateParams, $http, API, confirmationModalService) {
    var restartStage = function () {
      return API
        .one('pipelines')
        .one($stateParams.executionId)
        .one('stages', $scope.stage.id)
        .one('restart')
        .data({skip: false})
        .put()
        .then(function () {
          $scope.stage.isRestarting = true;
        });
    };

    this.restart = function () {
      let body = null;
      if ($scope.execution.isRunning) {
        body = '<p><strong>This pipeline is currently running - restarting this stage will result in multiple concurrently running pipelines.</strong></p>';
      }
      confirmationModalService.confirm({
        header: 'Really restart ' + $scope.stage.name + '?',
        buttonText: 'Restart ' + $scope.stage.name,
        body: body,
        submitMethod: restartStage
      });
    };
  });
