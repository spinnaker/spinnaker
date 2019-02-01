'use strict';

import * as _ from 'lodash';

import { ViewStateCache } from 'core/cache';

import { ReactModal } from 'core/presentation';

const angular = require('angular');

import { OVERRIDE_REGISTRY } from 'core/overrideRegistry/override.registry';
import { PipelineConfigValidator } from './validation/PipelineConfigValidator';
import { EXECUTION_BUILD_TITLE } from '../executionBuild/ExecutionBuildTitle';
import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';
import { ExecutionsTransformer } from 'core/pipeline/service/ExecutionsTransformer';
import { EditPipelineJsonModal } from 'core/pipeline/config/actions/json/EditPipelineJsonModal';

module.exports = angular
  .module('spinnaker.core.pipeline.config.pipelineConfigurer', [OVERRIDE_REGISTRY, EXECUTION_BUILD_TITLE])
  .directive('pipelineConfigurer', function() {
    return {
      restrict: 'E',
      scope: {
        pipeline: '=',
        application: '=',
        plan: '<',
        isTemplatedPipeline: '<',
        hasDynamicSource: '<',
        templateError: '<',
      },
      controller: 'PipelineConfigurerCtrl as pipelineConfigurerCtrl',
      templateUrl: require('./pipelineConfigurer.html'),
    };
  })
  .controller('PipelineConfigurerCtrl', function(
    $scope,
    $uibModal,
    $timeout,
    $window,
    $q,
    executionService,
    overrideRegistry,
    $location,
  ) {
    // For standard pipelines, a 'renderablePipeline' is just the pipeline config.
    // For templated pipelines, a 'renderablePipeline' is the pipeline template plan, and '$scope.pipeline' is the template config.
    $scope.renderablePipeline = $scope.plan || $scope.pipeline;
    // Watch for non-reference changes to renderablePipline and make them reference changes to make React happy
    $scope.$watch('renderablePipeline', (newValue, oldValue) => newValue !== oldValue && this.updatePipeline(), true);
    this.actionsTemplateUrl = overrideRegistry.getTemplate(
      'pipelineConfigActions',
      require('./actions/pipelineConfigActions.html'),
    );

    this.warningsPopover = require('./warnings.popover.html');

    PipelineConfigService.getHistory($scope.pipeline.id, $scope.pipeline.strategy, 2)
      .then(history => {
        if (history && history.length > 1) {
          $scope.viewState.hasHistory = true;
          this.setViewState({ hasHistory: true, loadingHistory: false });
        }
      })
      .finally(() => this.setViewState({ loadingHistory: false }));

    var configViewStateCache = ViewStateCache.get('pipelineConfig');

    function buildCacheKey() {
      return PipelineConfigService.buildViewStateCacheKey($scope.application.name, $scope.pipeline.id);
    }

    $scope.viewState = configViewStateCache.get(buildCacheKey()) || {
      section: 'triggers',
      stageIndex: 0,
      loading: false,
    };

    $scope.viewState.loadingHistory = true;

    let setOriginal = pipeline => {
      $scope.viewState.original = angular.toJson(pipeline);
      $scope.viewState.originalRenderablePipeline = angular.toJson($scope.renderablePipeline);
      this.updatePipeline();
    };

    let getOriginal = () => angular.fromJson($scope.viewState.original);

    const getOriginalRenderablePipeline = () => angular.fromJson($scope.viewState.originalRenderablePipeline);

    // keep it separate from viewState, since viewState is cached...
    $scope.navMenuState = {
      showMenu: false,
    };

    this.hideNavigationMenu = () => {
      // give the navigate method a chance to fire before hiding the menu
      $timeout(() => {
        $scope.navMenuState.showMenu = false;
      }, 200);
    };

    this.deletePipeline = () => {
      $uibModal.open({
        templateUrl: require('./actions/delete/deletePipelineModal.html'),
        controller: 'DeletePipelineModalCtrl',
        controllerAs: 'deletePipelineModalCtrl',
        resolve: {
          pipeline: () => $scope.pipeline,
          application: () => $scope.application,
        },
      });
    };

    this.addStage = (newStage = { isNew: true }) => {
      $scope.renderablePipeline.stages = $scope.renderablePipeline.stages || [];
      newStage.refId = Math.max(0, ...$scope.renderablePipeline.stages.map(s => Number(s.refId) || 0)) + 1 + '';
      newStage.requisiteStageRefIds = [];
      if ($scope.renderablePipeline.stages.length && $scope.viewState.section === 'stage') {
        newStage.requisiteStageRefIds.push($scope.renderablePipeline.stages[$scope.viewState.stageIndex].refId);
      }
      $scope.renderablePipeline.stages.push(newStage);
      this.navigateToStage($scope.renderablePipeline.stages.length - 1);
    };

    this.copyExistingStage = () => {
      $uibModal
        .open({
          templateUrl: require('./copyStage/copyStage.modal.html'),
          controller: 'CopyStageModalCtrl',
          controllerAs: 'copyStageModalCtrl',
          resolve: {
            application: () => $scope.application,
            forStrategyConfig: () => $scope.pipeline.strategy,
          },
        })
        .result.then(stageTemplate => ctrl.addStage(stageTemplate))
        .catch(() => {});
    };

    var ctrl = this;
    $scope.stageSortOptions = {
      axis: 'x',
      delay: 150,
      placeholder: 'btn btn-default drop-placeholder',
      'ui-floating': true,
      start: (e, ui) => {
        ui.placeholder.width(ui.helper.width()).height(ui.helper.height());
      },
      update: (e, ui) => {
        var itemScope = ui.item.scope(),
          currentPage = $scope.viewState.stageIndex,
          startingPagePosition = itemScope.$index,
          isCurrentPage = currentPage === startingPagePosition;

        $timeout(() => {
          itemScope = ui.item.scope(); // this is terrible but provides a hook for mocking in tests
          var newPagePosition = itemScope.$index;
          if (isCurrentPage) {
            ctrl.navigateToStage(newPagePosition);
          } else {
            var wasBefore = startingPagePosition < currentPage,
              isBefore = newPagePosition <= currentPage;
            if (wasBefore !== isBefore) {
              var newCurrentPage = isBefore ? currentPage + 1 : currentPage - 1;
              ctrl.navigateToStage(newCurrentPage);
            }
          }
        });
      },
    };

    this.renamePipeline = () => {
      $uibModal
        .open({
          templateUrl: require('./actions/rename/renamePipelineModal.html'),
          controller: 'RenamePipelineModalCtrl',
          controllerAs: 'renamePipelineModalCtrl',
          resolve: {
            pipeline: () => $scope.pipeline,
            application: () => $scope.application,
          },
        })
        .result.then(() => {
          setOriginal($scope.pipeline);
          markDirty();
        })
        .catch(() => {});
    };

    this.editPipelineJson = () => {
      const modalProps = { dialogClassName: 'modal-lg modal-fullscreen' };
      ReactModal.show(EditPipelineJsonModal, { pipeline: $scope.pipeline, plan: $scope.plan }, modalProps)
        .then(() => {
          $scope.$broadcast('pipeline-json-edited');
          this.updatePipeline();
        })
        .catch(() => {});
    };

    // Enabling a pipeline simply toggles the disabled flag - it does not save any pending changes
    this.enablePipeline = () => {
      $uibModal
        .open({
          templateUrl: require('./actions/enable/enablePipelineModal.html'),
          controller: 'EnablePipelineModalCtrl as ctrl',
          resolve: {
            pipeline: () => getOriginal(),
          },
        })
        .result.then(() => disableToggled(false))
        .catch(() => {});
    };

    // Disabling a pipeline also just toggles the disabled flag - it does not save any pending changes
    this.disablePipeline = () => {
      $uibModal
        .open({
          templateUrl: require('./actions/disable/disablePipelineModal.html'),
          controller: 'DisablePipelineModalCtrl as ctrl',
          resolve: {
            pipeline: () => getOriginal(),
          },
        })
        .result.then(() => disableToggled(true))
        .catch(() => {});
    };

    function disableToggled(isDisabled) {
      $scope.pipeline.disabled = isDisabled;
      let original = getOriginal();
      original.disabled = isDisabled;
      setOriginal(original);
    }

    // Locking a pipeline persists any pending changes
    this.lockPipeline = () => {
      $uibModal
        .open({
          templateUrl: require('./actions/lock/lockPipelineModal.html'),
          controller: 'LockPipelineModalCtrl as ctrl',
          resolve: {
            pipeline: () => $scope.pipeline,
          },
        })
        .result.then(() => setOriginal($scope.pipeline))
        .catch(() => {});
    };

    this.unlockPipeline = () => {
      $uibModal
        .open({
          templateUrl: require('./actions/unlock/unlockPipelineModal.html'),
          controller: 'unlockPipelineModalCtrl as ctrl',
          resolve: {
            pipeline: () => $scope.pipeline,
          },
        })
        .result.then(function() {
          delete $scope.pipeline.locked;
          setOriginal($scope.pipeline);
        })
        .catch(() => {});
    };

    this.showHistory = () => {
      $uibModal
        .open({
          templateUrl: require('./actions/history/showHistory.modal.html'),
          controller: 'ShowHistoryCtrl',
          controllerAs: 'ctrl',
          size: 'lg modal-fullscreen',
          resolve: {
            pipelineConfigId: () => $scope.pipeline.id,
            isStrategy: $scope.pipeline.strategy,
            currentConfig: () => ($scope.viewState.isDirty ? JSON.parse(angular.toJson($scope.pipeline)) : null),
          },
        })
        .result.then(newConfig => {
          $scope.renderablePipeline = newConfig;
          $scope.pipeline = newConfig;
          $scope.$broadcast('pipeline-json-edited');
          this.savePipeline();
        })
        .catch(() => {});
    };

    // Poor react setState
    this.setViewState = newViewState => {
      Object.assign($scope.viewState, newViewState);
      const viewState = _.clone($scope.viewState);
      $scope.$applyAsync(() => ($scope.viewState = viewState));
    };

    // Poor react setState
    this.updatePipeline = () => {
      $scope.$applyAsync(() => {
        $scope.renderablePipeline = _.clone($scope.renderablePipeline);
        // need to ensure references are maintained
        if ($scope.isTemplatedPipeline) {
          $scope.plan = $scope.renderablePipeline;
        } else {
          $scope.pipeline = $scope.renderablePipeline;
        }
      });
    };

    this.navigateToStage = (index, event) => {
      if (index < 0 || !$scope.renderablePipeline.stages || $scope.renderablePipeline.stages.length <= index) {
        this.setViewState({ section: 'triggers' });
        return;
      }
      this.setViewState({ section: 'stage', stageIndex: index });
      if (event && event.target && event.target.focus) {
        event.target.focus();
      }
    };

    this.navigateTo = stage => {
      if (stage.section === 'stage') {
        ctrl.navigateToStage(stage.index);
      } else {
        this.setViewState({ section: stage.section });
      }
    };

    // When using callbacks in a component that can be both angular and react, have to force binding in the angular world
    this.graphNodeClicked = this.navigateTo.bind(this);

    this.isActive = section => {
      return $scope.viewState.section === section;
    };

    this.stageIsActive = index => {
      return $scope.viewState.section === 'stage' && $scope.viewState.stageIndex === index;
    };

    this.removeStage = stage => {
      var stageIndex = $scope.renderablePipeline.stages.indexOf(stage);
      $scope.renderablePipeline.stages.splice(stageIndex, 1);
      $scope.renderablePipeline.stages.forEach(test => {
        if (stage.refId && test.requisiteStageRefIds) {
          if (test.requisiteStageRefIds.includes(stage.refId)) {
            test.requisiteStageRefIds = test.requisiteStageRefIds.filter(id => id !== stage.refId);
            if (!test.requisiteStageRefIds.length) {
              test.requisiteStageRefIds = [...stage.requisiteStageRefIds];
            }
          }
        }
      });
      if (stageIndex > 0) {
        this.setViewState({ stageIndex: $scope.viewState.stageIndex - 1 });
      }
      if (stageIndex === $scope.viewState.stageIndex && stageIndex === 0) {
        $scope.$broadcast('pipeline-json-edited');
      }
      if (!$scope.renderablePipeline.stages.length) {
        this.navigateTo({ section: 'triggers' });
      }
    };

    this.isValid = () => {
      return _.every($scope.pipeline.stages, 'name') && !ctrl.preventSave;
    };

    this.configureTemplate = () => {
      this.setViewState({ loading: true });
      $uibModal
        .open({
          size: 'lg',
          templateUrl: require('core/pipeline/config/templates/configurePipelineTemplateModal.html'),
          controller: 'ConfigurePipelineTemplateModalCtrl as ctrl',
          resolve: {
            application: () => $scope.application,
            pipelineTemplateConfig: () => _.cloneDeep($scope.pipeline),
            isNew: () => $scope.pipeline.isNew,
            pipelineId: () => $scope.pipeline.id,
            executionId: () => $scope.renderablePipeline.executionId,
          },
        })
        .result.then(({ plan, config }) => {
          $scope.pipeline = config;
          delete $scope.pipeline.isNew;
          $scope.renderablePipeline = plan;
        })
        .catch(() => {})
        .finally(() => this.setViewState({ loading: false }));
    };

    this.savePipeline = () => {
      this.setViewState({ saving: true });
      const toSave = _.cloneDeep($scope.pipeline);
      PipelineConfigService.savePipeline(toSave)
        .then(() => $scope.application.pipelineConfigs.refresh(true))
        .then(
          () => {
            $scope.viewState.hasHistory = true;
            setOriginal(toSave);
            markDirty();
            this.setViewState({ saving: false });
          },
          err =>
            this.setViewState({
              saveError: true,
              saving: false,
              saveErrorMessage: ctrl.getErrorMessage(err.data.message),
            }),
        );
    };

    this.getErrorMessage = errorMsg => {
      var msg = 'There was an error saving your pipeline';
      if (_.isString(errorMsg)) {
        msg += ': ' + errorMsg;
      }
      msg += '.';

      return msg;
    };

    this.getPipelineExecutions = () => {
      executionService
        .getExecutionsForConfigIds([$scope.pipeline.id], {
          limit: 5,
          transform: true,
          application: $scope.pipeline.application,
        })
        .then(executions => {
          executions.forEach(execution => ExecutionsTransformer.addBuildInfo(execution));
          $scope.pipelineExecutions = executions;
          if ($scope.plan && $scope.plan.executionId) {
            $scope.currentExecution = _.find($scope.pipelineExecutions, { id: $scope.plan.executionId });
          } else if ($location.search().executionId) {
            $scope.currentExecution = _.find($scope.pipelineExecutions, { id: $location.search().executionId });
          } else {
            $scope.currentExecution = $scope.pipelineExecutions[0];
          }
        })
        .catch(() => ($scope.pipelineExecutions = []));
    };

    this.revertPipelineChanges = () => {
      let original = getOriginal();
      Object.keys($scope.pipeline).forEach(key => {
        delete $scope.pipeline[key];
      });
      Object.assign($scope.pipeline, original);

      if ($scope.isTemplatedPipeline) {
        const originalRenderablePipeline = getOriginalRenderablePipeline();
        Object.assign($scope.renderablePipeline, originalRenderablePipeline);
        Object.keys($scope.renderablePipeline).forEach(key => {
          if (!originalRenderablePipeline.hasOwnProperty(key)) {
            delete $scope.renderablePipeline[key];
          }
        });
      }

      // if we were looking at a stage that no longer exists, move to the last stage
      if ($scope.viewState.section === 'stage') {
        var lastStage = $scope.renderablePipeline.stages.length - 1;
        if ($scope.viewState.stageIndex > lastStage) {
          this.setViewState({ stageIndex: lastStage });
        }
        if (!$scope.renderablePipeline.stages.length) {
          this.navigateTo({ section: 'triggers' });
        }
      }
      $scope.$broadcast('pipeline-reverted');
    };

    var markDirty = () => {
      if (!$scope.viewState.original) {
        setOriginal($scope.pipeline);
      }
      this.setViewState({ isDirty: $scope.viewState.original !== angular.toJson($scope.pipeline) });
    };

    // Poor bridge to update dirty flag when React stage field is updated
    this.stageFieldUpdated = () => markDirty();

    function cacheViewState() {
      const toCache = { section: $scope.viewState.section, stageIndex: $scope.viewState.stageIndex };
      configViewStateCache.put(buildCacheKey(), toCache);
    }

    $scope.$watch('pipeline', markDirty, true);
    $scope.$watch('viewState.original', markDirty);
    $scope.$watchGroup(['viewState.section', 'viewState.stageIndex'], cacheViewState);

    this.navigateTo({ section: $scope.viewState.section, index: $scope.viewState.stageIndex });

    this.getUrl = () => {
      return $location.absUrl();
    };

    const warningMessage = 'You have unsaved changes.\nAre you sure you want to navigate away from this page?';

    var confirmPageLeave = $scope.$on('$stateChangeStart', event => {
      if ($scope.viewState.isDirty) {
        if (!$window.confirm(warningMessage)) {
          event.preventDefault();
        }
      }
    });

    const validationSubscription = PipelineConfigValidator.subscribe(validations => {
      this.validations = validations;
      this.preventSave = validations.preventSave;
    });

    $window.onbeforeunload = () => {
      if ($scope.viewState.isDirty) {
        return warningMessage;
      }
    };

    $scope.$on('$destroy', () => {
      confirmPageLeave();
      validationSubscription.unsubscribe();
      $window.onbeforeunload = undefined;
    });

    if ($scope.hasDynamicSource) {
      this.getPipelineExecutions();
    }

    if ($scope.isTemplatedPipeline && $scope.pipeline.isNew && !$scope.hasDynamicSource) {
      this.configureTemplate();
    }
  });
