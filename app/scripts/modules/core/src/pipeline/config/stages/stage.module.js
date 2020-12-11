'use strict';

import { module } from 'angular';
import React from 'react';
import ReactDOM from 'react-dom';
import { defaultsDeep, extend, omit } from 'lodash';

import { AccountService } from 'core/account/AccountService';
import { API } from 'core/api';
import { BASE_EXECUTION_DETAILS_CTRL } from './common/baseExecutionDetails.controller';
import { ConfirmationModalService } from 'core/confirmationModal';
import { STAGE_NAME } from './StageName';
import { PipelineConfigService } from '../services/PipelineConfigService';
import { Registry } from 'core/registry';
import { StageConfigWrapper } from './StageConfigWrapper';
import { EditStageJsonModal } from './common/EditStageJsonModal';
import { ReactModal } from 'core/presentation';
import { PRODUCES_ARTIFACTS_REACT } from './producesArtifacts/ProducesArtifacts';
import { OVERRRIDE_FAILURE } from './overrideFailure/overrideFailure.module';
import { OVERRIDE_TIMEOUT_COMPONENT } from './overrideTimeout/overrideTimeout.module';
import { ApplicationReader } from 'core/application/service/ApplicationReader';
import { CORE_PIPELINE_CONFIG_STAGES_OPTIONALSTAGE_OPTIONALSTAGE_DIRECTIVE } from './optionalStage/optionalStage.directive';
import { CORE_PIPELINE_CONFIG_STAGES_FAILONFAILEDEXPRESSIONS_FAILONFAILEDEXPRESSIONS_DIRECTIVE } from './failOnFailedExpressions/failOnFailedExpressions.directive';
import { CORE_PIPELINE_CONFIG_STAGES_COMMON_STAGECONFIGFIELD_STAGECONFIGFIELD_DIRECTIVE } from './common/stageConfigField/stageConfigField.directive';

export const CORE_PIPELINE_CONFIG_STAGES_STAGE_MODULE = 'spinnaker.core.pipeline.config.stage';
export const name = CORE_PIPELINE_CONFIG_STAGES_STAGE_MODULE; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_STAGES_STAGE_MODULE, [
  PRODUCES_ARTIFACTS_REACT,
  BASE_EXECUTION_DETAILS_CTRL,
  STAGE_NAME,
  OVERRIDE_TIMEOUT_COMPONENT,
  OVERRRIDE_FAILURE,
  CORE_PIPELINE_CONFIG_STAGES_OPTIONALSTAGE_OPTIONALSTAGE_DIRECTIVE,
  CORE_PIPELINE_CONFIG_STAGES_FAILONFAILEDEXPRESSIONS_FAILONFAILEDEXPRESSIONS_DIRECTIVE,
  CORE_PIPELINE_CONFIG_STAGES_COMMON_STAGECONFIGFIELD_STAGECONFIGFIELD_DIRECTIVE,
])
  .directive('pipelineConfigStage', function () {
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
      link: function (scope, elem, attrs, pipelineConfigurerCtrl) {
        scope.pipelineConfigurerCtrl = pipelineConfigurerCtrl;
      },
    };
  })
  .controller('StageConfigCtrl', [
    '$scope',
    '$element',
    '$compile',
    '$controller',
    '$templateCache',
    function ($scope, $element, $compile, $controller, $templateCache) {
      let lastStageScope, reactComponentMounted;
      let appPermissions = {};
      let appRoles = [];
      $scope.options = {
        stageTypes: [],
        selectedStageType: null,
        stageRoles: [],
      };

      AccountService.applicationAccounts($scope.application).then((accounts) => {
        $scope.options.stageTypes = Registry.pipeline.getConfigurableStageTypes(accounts);
        $scope.showProviders = new Set(accounts.map((a) => a.cloudProvider)).size > 1;
      });

      if ($scope.pipeline.strategy) {
        $scope.options.stageTypes = $scope.options.stageTypes.filter((stageType) => {
          return stageType.strategy || false;
        });
      }

      function getConfig(stage) {
        return Registry.pipeline.getStageConfig(stage);
      }

      $scope.groupDependencyOptions = function (stage) {
        const requisiteStageRefIds = $scope.stage.requisiteStageRefIds || [];
        return stage.available
          ? 'Available'
          : requisiteStageRefIds.includes(stage.refId)
          ? null
          : 'Downstream dependencies (unavailable)';
      };

      $scope.stageProducesArtifacts = function () {
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

      $scope.producesArtifactsChanged = function (artifacts) {
        $scope.$applyAsync(() => {
          $scope.stage.expectedArtifacts = artifacts;
        });
      };

      $scope.updateAvailableDependencyStages = function () {
        const availableDependencyStages = PipelineConfigService.getDependencyCandidateStages(
          $scope.pipeline,
          $scope.stage,
        );
        $scope.options.dependencies = availableDependencyStages.map(function (stage) {
          return {
            name: stage.name,
            refId: stage.refId,
            available: true,
          };
        });

        $scope.pipeline.stages.forEach(function (stage) {
          if (stage !== $scope.stage && !availableDependencyStages.includes(stage)) {
            $scope.options.dependencies.push({
              name: stage.name,
              refId: stage.refId,
            });
          }
        });
      };

      $scope.getApplicationPermissions = function() {
          ApplicationReader.getApplicationPermissions($scope.application.name).then(result => {
              appPermissions = result;
              if (appPermissions) {
                  const readArray = appPermissions.READ || [];
                  const writeArray = appPermissions.WRITE || [];
                  const executeArray = appPermissions.EXECUTE || [];
                  appRoles = _.union(readArray, writeArray, executeArray);
                  appRoles = Array.from(new Set(appRoles));
                  $scope.updateAvailableStageRoles();
              }
          });
      };

      $scope.updateAvailableStageRoles = function() {
          $scope.options.stageRoles = appRoles.map(function(value, index) {
              return {
                  name: value,
                  roleId: value,
                  id: index,
                  available: true,
              };
          });
      };

      this.editStageJson = () => {
        const modalProps = { dialogClassName: 'modal-lg modal-fullscreen' };
        ReactModal.show(EditStageJsonModal, { stage: $scope.stage }, modalProps)
          .then(() => {
            $scope.$applyAsync(() => $scope.$broadcast('pipeline-json-edited'));
          })
          .catch(() => {}); // user closed modal
      };

      this.selectStageType = (stage) => {
        $scope.stage.type = stage.key;
        this.selectStage();
        // clear stage-specific fields
        Object.keys($scope.stage).forEach((k) => {
          if (!['requisiteStageRefIds', 'refId', 'isNew', 'name', 'type'].includes(k)) {
            delete $scope.stage[k];
          }
        });
      };

      this.updateStageField = (changes) => {
        extend($scope.stage, changes);
        $scope.stageFieldUpdated();
      };

      this.selectStage = function (newVal, oldVal) {
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
        const type = $scope.stage.type;
        const stageScope = $scope.$new();

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
        $scope.$on('$destroy', function () {
          stageScope.$destroy();
        });

        if (type && stageDetailsNode) {
          const config = getConfig($scope.stage);
          if (config) {
            $scope.canConfigureNotifications = !$scope.pipeline.strategy && !config.disableNotifications;
            $scope.description = config.description;
            $scope.extendedDescription = config.extendedDescription;
            $scope.label = config.label;
            if (config.addAliasToConfig) {
              $scope.stage.alias = config.alias;
            }
            if (config.defaults) {
              defaultsDeep($scope.stage, config.defaults);
            }
            if (config.useBaseProvider || config.provides) {
              config.component = null;
              config.templateUrl = require('./baseProviderStage/baseProviderStage.html');
              config.controller = 'BaseProviderStageCtrl as baseProviderStageCtrl';
            }
            updateStageName(config, oldVal);
            applyConfigController(config, stageScope);

            const props = {
              application: $scope.application,
              stageFieldUpdated: $scope.stageFieldUpdated,
              updateStageField: (changes) => {
                // Reserved fields should not be mutated from with a StageConfig component
                const allowedChanges = omit(changes, ['requisiteStageRefIds', 'refId', 'isNew', 'name', 'type']);

                extend($scope.stage, allowedChanges);
                $scope.stageFieldUpdated();
              },
              pipeline: $scope.pipeline,
              stage: $scope.stage,
              component: config.component,
              configuration: config.configuration,
            };
            if (config.useBaseProvider || config.provides) {
              stageScope.reactPropsForBaseProviderStage = props;
            }
            if (config.component) {
              ReactDOM.render(React.createElement(StageConfigWrapper, props), stageDetailsNode);
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

        updateStageConfig($scope.stage);
      };

      function applyConfigController(config, stageScope) {
        if (config.controller) {
          const ctrl = config.controller.split(' as ');
          const controller = $controller(ctrl[0], {
            $scope: stageScope,
            stage: $scope.stage,
            viewState: $scope.viewState,
          });
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
          const oldConfig = getConfig({ type: oldVal });
          if (oldConfig && $scope.stage.name === oldConfig.label) {
            $scope.stage.name = config.label;
          }
        }
        if (!$scope.stage.name && config.label) {
          $scope.stage.name = config.label;
        }
      }

      function updateStageConfig(stage) {
        $scope.$applyAsync(() => {
          $scope.stageConfig = getConfig(stage);
        });
      }

      this.updateNotifications = function (notifications) {
        $scope.$applyAsync(() => {
          $scope.stage.notifications = notifications;
        });
      };

      this.handleSendNotificationsChanged = function () {
        $scope.$applyAsync(() => {
          $scope.stage.sendNotifications = !$scope.stage.sendNotifications;
        });
      };

      $scope.getApplicationPermissions();
      $scope.$on('pipeline-reverted', this.selectStage);
      $scope.$on('pipeline-json-edited', this.selectStage);
      $scope.$watch('stage.type', this.selectStage);
      $scope.$watch('viewState.stageIndex', this.selectStage);
      $scope.$watch('stage.refId', this.selectStage);
    },
  ])
  .controller('RestartStageCtrl', [
    '$scope',
    '$stateParams',
    function ($scope, $stateParams) {
      const restartStage = function () {
        return API.one('pipelines')
          .one($stateParams.executionId)
          .one('stages', $scope.stage.id)
          .one('restart')
          .data({ skip: false })
          .put()
          .then(function () {
            $scope.stage.isRestarting = true;
          });
      };

      this.restart = function () {
        let body = null;
        if ($scope.execution.isRunning) {
          body =
            '<p><strong>This pipeline is currently running - restarting this stage will result in multiple concurrently running pipelines.</strong></p>';
        }
        ConfirmationModalService.confirm({
          header: 'Really restart ' + $scope.stage.name + '?',
          buttonText: 'Restart ' + $scope.stage.name,
          body: body,
          submitMethod: restartStage,
        });
      };
    },
  ])
  .filter('stageTypeMatch', () => {
    return (stageTypes, search) => {
      const q = search.toLowerCase();
      return stageTypes
        .filter((s) => s.label.toLowerCase().includes(q) || s.description.toLowerCase().includes(q))
        .sort((a, b) => {
          const aLabel = a.label.toLowerCase();
          const bLabel = b.label.toLowerCase();
          const aDescription = a.description.toLowerCase();
          const bDescription = b.description.toLowerCase();
          if (aLabel.includes(q) && !bLabel.includes(q)) {
            return -1;
          }
          if (!aLabel.includes(q) && bLabel.includes(q)) {
            return 1;
          }
          if (aLabel.includes(q) && bLabel.includes(q)) {
            return aLabel.indexOf(q) - bLabel.indexOf(q);
          }
          return aDescription.indexOf(q) - bDescription.indexOf(q);
        });
    };
  });
