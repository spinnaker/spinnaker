'use strict';

const angular = require('angular');
import * as React from 'react';
import * as ReactDOM from 'react-dom';

import { AccountService } from 'core/account/AccountService';
import { API } from 'core/api';
import { BASE_EXECUTION_DETAILS_CTRL } from './core/baseExecutionDetails.controller';
import { CONFIRMATION_MODAL_SERVICE } from 'core/confirmationModal/confirmationModal.service';
import { EDIT_STAGE_JSON_CONTROLLER } from './core/editStageJson.controller';
import { STAGE_NAME } from './StageName';
import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';
import { Registry } from 'core/registry';

module.exports = angular
  .module('spinnaker.core.pipeline.config.stage', [
    BASE_EXECUTION_DETAILS_CTRL,
    EDIT_STAGE_JSON_CONTROLLER,
    STAGE_NAME,
    require('./overrideTimeout/overrideTimeout.directive.js').name,
    require('./overrideFailure/overrideFailure.component.js').name,
    require('./optionalStage/optionalStage.directive.js').name,
    require('./failOnFailedExpressions/failOnFailedExpressions.directive.js').name,
    CONFIRMATION_MODAL_SERVICE,
    require('./core/stageConfigField/stageConfigField.directive.js').name,
  ])
  .directive('pipelineConfigStage', function() {
    return {
      restrict: 'E',
      require: '^pipelineConfigurer',
      scope: {
        viewState: '=',
        application: '=',
        pipeline: '=',
        stageFieldUpdated: '<',
      },
      controller: 'StageConfigCtrl as stageConfigCtrl',
      templateUrl: require('./stage.html'),
      link: function(scope, elem, attrs, pipelineConfigurerCtrl) {
        scope.pipelineConfigurerCtrl = pipelineConfigurerCtrl;
      },
    };
  })
  .controller('StageConfigCtrl', function($scope, $element, $compile, $controller, $templateCache, $uibModal) {
    var lastStageScope, reactComponentMounted;

    $scope.options = {
      stageTypes: [],
      selectedStageType: null,
    };

    AccountService.applicationAccounts($scope.application).then(accounts => {
      $scope.options.stageTypes = Registry.pipeline.getConfigurableStageTypes(accounts);
      $scope.showProviders = new Set(accounts.map(a => a.cloudProvider)).size > 1;
    });

    if ($scope.pipeline.strategy) {
      $scope.options.stageTypes = $scope.options.stageTypes.filter(stageType => {
        return stageType.strategy || false;
      });
    }

    function getConfig(stage) {
      return Registry.pipeline.getStageConfig(stage);
    }

    $scope.groupDependencyOptions = function(stage) {
      var requisiteStageRefIds = $scope.stage.requisiteStageRefIds || [];
      return stage.available
        ? 'Available'
        : requisiteStageRefIds.includes(stage.refId)
          ? null
          : 'Downstream dependencies (unavailable)';
    };

    $scope.stageProducesArtifacts = function() {
      if (!$scope.stage) {
        return false;
      }

      const stageConfig = Registry.pipeline.getStageConfig($scope.stage);

      if (!stageConfig) {
        return false;
      } else {
        return !!stageConfig.producesArtifacts;
      }
    };

    $scope.updateAvailableDependencyStages = function() {
      var availableDependencyStages = PipelineConfigService.getDependencyCandidateStages($scope.pipeline, $scope.stage);
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

    this.editStageJson = () => {
      $uibModal
        .open({
          size: 'lg modal-fullscreen',
          templateUrl: require('./core/editStageJson.modal.html'),
          controller: 'editStageJsonCtrl as $ctrl',
          resolve: {
            stage: () => $scope.stage,
          },
        })
        .result.then(() => {
          $scope.$broadcast('pipeline-json-edited');
        })
        .catch(() => {});
    };

    this.selectStageType = stage => {
      $scope.stage.type = stage.key;
      if (stage.addAliasToConfig) {
        $scope.stage.alias = stage.alias;
      }
      this.selectStage();
    };

    this.selectStage = function(newVal, oldVal) {
      const stageDetailsNode = $element.find('.stage-details').get(0);
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
      if (reactComponentMounted) {
        ReactDOM.unmountComponentAtNode(stageDetailsNode);
        reactComponentMounted = false;
      } else {
        $element.find('.stage-details').html('');
      }

      $scope.description = '';
      if (lastStageScope) {
        lastStageScope.$destroy();
      }
      $scope.extendedDescription = '';
      lastStageScope = stageScope;
      $scope.$on('$destroy', function() {
        stageScope.$destroy();
      });

      if (type && stageDetailsNode) {
        let config = getConfig($scope.stage);
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

          if (config.component) {
            const StageConfig = config.component;
            const props = {
              application: $scope.application,
              stageFieldUpdated: $scope.stageFieldUpdated,
              stage: $scope.stage,
            };
            ReactDOM.render(React.createElement(StageConfig, props), stageDetailsNode);
          } else {
            const template = $templateCache.get(config.templateUrl);
            const templateBody = $compile(template)(stageScope);
            $element.find('.stage-details').html(templateBody);
          }
          reactComponentMounted = !!config.component;
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
        var controller = $controller(ctrl[0], { $scope: stageScope, stage: $scope.stage, viewState: $scope.viewState });
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
        var oldConfig = getConfig({ type: oldVal });
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
  .controller('RestartStageCtrl', function($scope, $stateParams, confirmationModalService) {
    var restartStage = function() {
      return API.one('pipelines')
        .one($stateParams.executionId)
        .one('stages', $scope.stage.id)
        .one('restart')
        .data({ skip: false })
        .put()
        .then(function() {
          $scope.stage.isRestarting = true;
        });
    };

    this.restart = function() {
      let body = null;
      if ($scope.execution.isRunning) {
        body =
          '<p><strong>This pipeline is currently running - restarting this stage will result in multiple concurrently running pipelines.</strong></p>';
      }
      confirmationModalService.confirm({
        header: 'Really restart ' + $scope.stage.name + '?',
        buttonText: 'Restart ' + $scope.stage.name,
        body: body,
        submitMethod: restartStage,
      });
    };
  });
