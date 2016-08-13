'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.pipelineConfigurer', [
  require('../../overrideRegistry/override.registry.js'),
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
  .controller('PipelineConfigurerCtrl', function($scope, $uibModal, $timeout, _,
                                                 dirtyPipelineTracker, pipelineConfigService, viewStateCache, overrideRegistry, $location) {

    this.actionsTemplateUrl = overrideRegistry.getTemplate('pipelineConfigActions', require('./actions/pipelineConfigActions.html'));

    let original = _.cloneDeep($scope.pipeline);

    pipelineConfigService.getHistory($scope.pipeline.id, 2).then(history => {
      if (history && history.length > 1) {
        $scope.viewState.hasHistory = true;
      }
    });

    var configViewStateCache = viewStateCache.pipelineConfig;

    function buildCacheKey() {
      return pipelineConfigService.buildViewStateCacheKey($scope.application.name, $scope.pipeline.name);
    }

    $scope.viewState = configViewStateCache.get(buildCacheKey()) || {
      expanded: true,
      section: 'triggers',
      stageIndex: 0,
      originalPipelineName: $scope.pipeline.name,
      saving: false,
    };

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
          pipeline: function() { return $scope.pipeline; },
          application: function() { return $scope.application; }
        }
      });
    };

    this.addStage = function() {
      var newStage = { isNew: true };
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
          pipeline: function() { return original; },
          application: function() { return $scope.application; }
        }
      }).result.then(function() {
          $scope.pipeline.name = original.name;
          $scope.viewState.original = angular.toJson(getPlain(original));
          $scope.viewState.originalPipelineName = original.name;
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
          pipeline: function() { return $scope.pipeline; },
        }
      }).result.then(function() {
        $scope.$broadcast('pipeline-json-edited');
      });
    };

    this.enablePipeline = () => {
      $uibModal.open({
        templateUrl: require('./actions/enable/enablePipelineModal.html'),
        controller: 'EnablePipelineModalCtrl as ctrl',
        resolve: {
          pipeline: () => original
        }
      }).result.then(disableToggled);
    };

    this.disablePipeline = () => {
      $uibModal.open({
        templateUrl: require('./actions/disable/disablePipelineModal.html'),
        controller: 'DisablePipelineModalCtrl as ctrl',
        resolve: {
          pipeline: () => original
        }
      }).result.then(disableToggled);
    };

    function disableToggled() {
      $scope.pipeline.disabled = !$scope.pipeline.disabled;
    }

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
      var pipeline = $scope.pipeline,
          viewState = $scope.viewState;

      $scope.viewState.saving = true;
      pipelineConfigService.savePipeline(pipeline)
        .then($scope.application.pipelineConfigs.refresh)
        .then(
          function() {
            viewState.original = angular.toJson(getPlain(pipeline));
            viewState.originalPipelineName = pipeline.name;
            markDirty();
            $scope.viewState.saving = false;
          },
          function() {
            $scope.viewState.saveError = true;
            $scope.viewState.saving = false;
          }
        );
    };

    this.revertPipelineChanges = function() {
      var original = angular.fromJson($scope.viewState.original);
      if (original.parallel) {
        $scope.pipeline.parallel = true;
      } else {
        delete $scope.pipeline.parallel;
      }
      $scope.pipeline.stages = original.stages;
      $scope.pipeline.triggers = original.triggers;
      $scope.pipeline.notifications = original.notifications;
      $scope.pipeline.persistedProperties = original.persistedProperties;
      $scope.pipeline.parameterConfig = original.parameterConfig;
      $scope.pipeline.name = $scope.viewState.originalPipelineName;

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

    function cleanStageForDiffing(stage) {
      // TODO: Consider removing this altogether after migrating existing pipelines
      if (stage.cloudProviderType === 'aws') {
        delete stage.cloudProviderType;
      }
    }

    function getPlain(pipeline) {
      var base = pipeline;
      var copy = _.cloneDeep(base);
      copy.stages.forEach(cleanStageForDiffing);
      return {
        stages: copy.stages,
        triggers: copy.triggers,
        parallel: copy.parallel,
        appConfig: copy.appConfig || {},
        limitConcurrent: copy.limitConcurrent,
        keepWaitingPipelines: copy.keepWaitingPipelines,
        parameterConfig: copy.parameterConfig,
        notifications: copy.notifications,
        persistedProperties: copy.persistedProperties,
      };
    }

    var markDirty = function markDirty() {
      if (!$scope.viewState.original) {
        $scope.viewState.original = angular.toJson(getPlain($scope.pipeline));
      }
      $scope.viewState.isDirty = $scope.viewState.original !== angular.toJson(getPlain($scope.pipeline));
      if ($scope.viewState.isDirty) {
        dirtyPipelineTracker.add($scope.pipeline.name);
//        console.warn('dirty:');
//        console.warn($scope.viewState.original);
//        console.warn(angular.toJson(getPlain($scope.pipeline)));
      } else {
        dirtyPipelineTracker.remove($scope.pipeline.name);
      }
    };

    function cacheViewState() {
      var toCache = angular.copy($scope.viewState);
      delete toCache.original;
      configViewStateCache.put(buildCacheKey(), toCache);
    }

    $scope.$on('toggle-expansion', (event, expanded) => $scope.viewState.expanded = expanded);

    $scope.$watch('pipeline', markDirty, true);
    $scope.$watch('viewState.original', markDirty, true);
    $scope.$watch('viewState', cacheViewState, true);
    $scope.$watch('pipeline.name', cacheViewState);

    this.navigateTo({section: $scope.viewState.section, index: $scope.viewState.stageIndex});


    this.getUrl = () => {
      return $location.absUrl();
    };

  });
