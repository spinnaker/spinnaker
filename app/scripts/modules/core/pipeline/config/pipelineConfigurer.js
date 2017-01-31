'use strict';

import * as _ from 'lodash';

let angular = require('angular');

import {OVERRIDE_REGISTRY} from 'core/overrideRegistry/override.registry';
import {PIPELINE_CONFIG_SERVICE} from 'core/pipeline/config/services/pipelineConfig.service';
import {PIPELINE_CONFIG_VALIDATOR} from './validation/pipelineConfig.validator';

module.exports = angular.module('spinnaker.core.pipeline.config.pipelineConfigurer', [
  OVERRIDE_REGISTRY,
  PIPELINE_CONFIG_SERVICE,
  PIPELINE_CONFIG_VALIDATOR,
])
  .directive('pipelineConfigurer', function() {
    return {
      restrict: 'E',
      scope: {
        pipeline: '=',
        application: '='
      },
      controller: 'PipelineConfigurerCtrl as pipelineConfigurerCtrl',
      templateUrl: require('./pipelineConfigurer.html'),
    };
  })
  .controller('PipelineConfigurerCtrl', function($scope, $uibModal, $timeout, $window, pageTitleService,
                                                 pipelineConfigValidator,
                                                 pipelineConfigService, viewStateCache, overrideRegistry, $location) {

    this.actionsTemplateUrl = overrideRegistry.getTemplate('pipelineConfigActions', require('./actions/pipelineConfigActions.html'));

    this.warningsPopover = require('./warnings.popover.html');

    pipelineConfigService.getHistory($scope.pipeline.id, 2).then(history => {
      if (history && history.length > 1) {
        $scope.viewState.hasHistory = true;
      }
    });

    var configViewStateCache = viewStateCache.get('pipelineConfig');

    function buildCacheKey() {
      return pipelineConfigService.buildViewStateCacheKey($scope.application.name, $scope.pipeline.id);
    }

    $scope.viewState = configViewStateCache.get(buildCacheKey()) || {
      section: 'triggers',
      stageIndex: 0,
      saving: false,
    };

    let setOriginal = (pipeline) => $scope.viewState.original = angular.toJson(pipeline);

    let getOriginal = () => angular.fromJson($scope.viewState.original);

    // keep it separate from viewState, since viewState is cached...
    $scope.navMenuState = {
      showMenu: false,
    };

    this.hideNavigationMenu = () => {
      // give the navigate method a chance to fire before hiding the menu
      $timeout(() => {
        $scope.navMenuState.showMenu = false;
      }, 200 );
    };

    this.deletePipeline = function() {
      $uibModal.open({
        templateUrl: require('./actions/delete/deletePipelineModal.html'),
        controller: 'DeletePipelineModalCtrl',
        controllerAs: 'deletePipelineModalCtrl',
        resolve: {
          pipeline: () => $scope.pipeline,
          application: () => $scope.application,
        }
      });
    };

    this.addStage = function(newStage = { isNew: true }) {
      $scope.pipeline.stages = $scope.pipeline.stages || [];
      if ($scope.pipeline.parallel) {
        newStage.refId = Math.max(0, ...$scope.pipeline.stages.map(s => Number(s.refId) || 0)) + 1 + '';
        newStage.requisiteStageRefIds = [];
        if ($scope.pipeline.stages.length && $scope.viewState.section === 'stage') {
          newStage.requisiteStageRefIds.push($scope.pipeline.stages[$scope.viewState.stageIndex].refId);
        }
      }
      $scope.pipeline.stages.push(newStage);
      this.navigateToStage($scope.pipeline.stages.length - 1);
    };

    this.copyExistingStage = function() {
      $uibModal.open({
        templateUrl: require('./copyStage/copyStage.modal.html'),
        controller: 'CopyStageModalCtrl',
        controllerAs: 'copyStageModalCtrl',
        resolve: {
          application: () => $scope.application,
          forStrategyConfig: () => $scope.pipeline.strategy,
        }
      }).result.then(stageTemplate => ctrl.addStage(stageTemplate));
    };

    var ctrl = this;
    $scope.stageSortOptions = {
      axis: 'x',
      delay: 150,
      placeholder: 'btn btn-default drop-placeholder',
      'ui-floating': true,
      start: function(e, ui) {
        ui.placeholder.width(ui.helper.width()).height(ui.helper.height());
      },
      update: function(e, ui) {
        var itemScope = ui.item.scope(),
          currentPage = $scope.viewState.stageIndex,
          startingPagePosition = itemScope.$index,
          isCurrentPage = currentPage === startingPagePosition;

        $timeout(function() {
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
      }
    };

    this.renamePipeline = function() {
      var original = $scope.pipeline;
      original.name = $scope.pipeline.name;
      $uibModal.open({
        templateUrl: require('./actions/rename/renamePipelineModal.html'),
        controller: 'RenamePipelineModalCtrl',
        controllerAs: 'renamePipelineModalCtrl',
        resolve: {
          pipeline: () => $scope.pipeline,
          application: () => $scope.application
        }
      }).result.then(() => {
          setOriginal($scope.pipeline);
          markDirty();
        });
    };

    this.editPipelineJson = function() {
      $uibModal.open({
        templateUrl: require('./actions/json/editPipelineJsonModal.html'),
        controller: 'EditPipelineJsonModalCtrl',
        controllerAs: 'editPipelineJsonModalCtrl',
        size: 'lg modal-fullscreen',
        resolve: {
          pipeline: () => $scope.pipeline,
        }
      }).result.then(function() {
        $scope.$broadcast('pipeline-json-edited');
      });
    };

    // Enabling a pipeline simply toggles the disabled flag - it does not save any pending changes
    this.enablePipeline = () => {
      $uibModal.open({
        templateUrl: require('./actions/enable/enablePipelineModal.html'),
        controller: 'EnablePipelineModalCtrl as ctrl',
        resolve: {
          pipeline: () => getOriginal()
        }
      }).result.then(() => disableToggled(false));
    };

    // Disabling a pipeline also just toggles the disabled flag - it does not save any pending changes
    this.disablePipeline = () => {
      $uibModal.open({
        templateUrl: require('./actions/disable/disablePipelineModal.html'),
        controller: 'DisablePipelineModalCtrl as ctrl',
        resolve: {
          pipeline: () => getOriginal()
        }
      }).result.then(() => disableToggled(true));
    };

    function disableToggled(isDisabled) {
      $scope.pipeline.disabled = isDisabled;
      let original = getOriginal();
      original.disabled = isDisabled;
      setOriginal(original);
    }

    // Locking a pipeline persists any pending changes
    this.lockPipeline = () => {
      $uibModal.open({
        templateUrl: require('./actions/lock/lockPipelineModal.html'),
        controller: 'LockPipelineModalCtrl as ctrl',
        resolve: {
          pipeline: () => $scope.pipeline
        }
      }).result.then(function() {
        setOriginal($scope.pipeline);
      });
    };

    this.unlockPipeline = () => {
      $uibModal.open({
        templateUrl: require('./actions/unlock/unlockPipelineModal.html'),
        controller: 'unlockPipelineModalCtrl as ctrl',
        resolve: {
          pipeline: () => $scope.pipeline
        }
      }).result.then(function () {
        delete $scope.pipeline.locked;
        setOriginal($scope.pipeline);
      });
    };

    this.showHistory = () => {
      $uibModal.open({
        templateUrl: require('./actions/history/showHistory.modal.html'),
        controller: 'ShowHistoryCtrl',
        controllerAs: 'ctrl',
        size: 'lg modal-fullscreen',
        resolve: {
          pipelineConfigId: () => $scope.pipeline.id,
          currentConfig: () => $scope.viewState.isDirty ? JSON.parse(angular.toJson($scope.pipeline)) : null,
        }
      }).result.then(newConfig => {
        $scope.pipeline = newConfig;
        this.savePipeline();
      });
    };

    this.navigateToStage = function(index, event) {
      if (index < 0 || !$scope.pipeline.stages || $scope.pipeline.stages.length <= index) {
        $scope.viewState.section = 'triggers';
        return;
      }
      $scope.viewState.section = 'stage';
      $scope.viewState.stageIndex = index;
      if (event && event.target && event.target.focus) {
        event.target.focus();
      }
    };

    this.navigateTo = function(stage) {
      $scope.viewState.section = stage.section;
      if (stage.section === 'stage') {
        ctrl.navigateToStage(stage.index);
      }
    };

    this.isActive = function(section) {
      return $scope.viewState.section === section;
    };

    this.stageIsActive = function(index) {
      return $scope.viewState.section === 'stage' && $scope.viewState.stageIndex === index;
    };

    this.removeStage = function(stage) {
      var stageIndex = $scope.pipeline.stages.indexOf(stage);
      $scope.pipeline.stages.splice(stageIndex, 1);
      $scope.pipeline.stages.forEach(function(test) {
        if (stage.refId && test.requisiteStageRefIds) {
          test.requisiteStageRefIds = _.without(test.requisiteStageRefIds, stage.refId);
        }
      });
      if (stageIndex > 0) {
        $scope.viewState.stageIndex--;
      }
      if (!$scope.pipeline.stages.length) {
        this.navigateTo({section: 'triggers'});
      }
    };

    this.isValid = function() {
      return _.every($scope.pipeline.stages, 'name');
    };

    this.savePipeline = function() {
      var pipeline = $scope.pipeline;

      $scope.viewState.saving = true;
      pipelineConfigService.savePipeline(pipeline)
        .then(() => $scope.application.pipelineConfigs.refresh())
        .then(
          function() {
            setOriginal($scope.pipeline);
            markDirty();
            $scope.viewState.saving = false;
          },
          function(err) {
            $scope.viewState.saveError = true;
            $scope.viewState.saving = false;
            $scope.viewState.saveErrorMessage = ctrl.getErrorMessage(err.data.message);
          }
        );
    };

    this.getErrorMessage = function(errorMsg) {
      var msg = 'There was an error saving your pipeline';
      if (_.isString(errorMsg)) {
        msg += ': ' + errorMsg;
      }
      msg += '.';

      return msg;
    };

    this.revertPipelineChanges = function() {
      let original = getOriginal();
      Object.assign($scope.pipeline, original);
      Object.keys($scope.pipeline).forEach(key => {
        if (!original.hasOwnProperty(key)) {
          delete $scope.pipeline[key];
        }
      });

      // if we were looking at a stage that no longer exists, move to the last stage
      if ($scope.viewState.section === 'stage') {
        var lastStage = $scope.pipeline.stages.length - 1;
        if ($scope.viewState.stageIndex > lastStage) {
          $scope.viewState.stageIndex = lastStage;
        }
        if (!$scope.pipeline.stages.length) {
          this.navigateTo({section: 'triggers'});
        }
      }
      $scope.$broadcast('pipeline-reverted');
    };

    var markDirty = function markDirty() {
      if (!$scope.viewState.original) {
        setOriginal($scope.pipeline);
      }
      $scope.viewState.isDirty = $scope.viewState.original !== angular.toJson($scope.pipeline);
    };

    function cacheViewState() {
      var toCache = angular.copy($scope.viewState);
      delete toCache.original;
      configViewStateCache.put(buildCacheKey(), toCache);
    }

    $scope.$watch('pipeline', markDirty, true);
    $scope.$watch('viewState.original', markDirty);
    $scope.$watch('viewState', cacheViewState, true);

    this.navigateTo({section: $scope.viewState.section, index: $scope.viewState.stageIndex});


    this.getUrl = () => {
      return $location.absUrl();
    };

    const warningMessage = 'You have unsaved changes.\nAre you sure you want to navigate away from this page?';

    var confirmPageLeave = $scope.$on('$stateChangeStart', function(event) {
      if ($scope.viewState.isDirty) {
        if (!$window.confirm(warningMessage)) {
          event.preventDefault();
          pageTitleService.handleRoutingCanceled();
        }
      }
    });

    const validationSubscription = pipelineConfigValidator.subscribe((validations) => {
      this.validations = validations;
    });

    $window.onbeforeunload = function() {
      if ($scope.viewState.isDirty) {
        return warningMessage;
      }
    };

    $scope.$on('$destroy', function() {
      confirmPageLeave();
      validationSubscription.unsubscribe();
      $window.onbeforeunload = undefined;
    });

  });
