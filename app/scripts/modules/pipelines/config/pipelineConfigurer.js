'use strict';

angular.module('deckApp.pipelines')
  .directive('pipelineConfigurer', function() {
    return {
      restrict: 'E',
      scope: {
        pipeline: '=',
        application: '='
      },
      controller: 'PipelineConfigurerCtrl as pipelineConfigurerCtrl',
      templateUrl: 'scripts/modules/pipelines/config/pipelineConfigurer.html'
    };
  })
  .controller('PipelineConfigurerCtrl', function($scope, $modal, $timeout, _,
                                                 dirtyPipelineTracker, pipelineConfigService, viewStateCache,
                                                 settings) {

    var configViewStateCache = viewStateCache.pipelineConfig;

    function buildCacheKey() {
      return pipelineConfigService.buildViewStateCacheKey($scope.application.name, $scope.pipeline.name);
    }

    if (settings.feature.parallelPipelines && !$scope.pipeline.stageCounter && $scope.pipeline.stages.length && !$scope.pipeline.stages[0].refId) {
      $scope.pipeline.stageCounter = 0;
      $scope.pipeline.stages.forEach(function(stage) {
        $scope.pipeline.stageCounter++;
        stage.refId = $scope.pipeline.stageCounter;
        if (stage.refId > 1) {
          stage.requisiteStageRefIds = [$scope.pipeline.stageCounter - 1];
        } else {
          stage.requisiteStageRefIds = [];
        }
      });
      $scope.pipeline.stageCounter = $scope.pipeline.stages.length;
    }

    $scope.viewState = configViewStateCache.get(buildCacheKey()) || {
      expanded: true,
      section: 'triggers',
      stageIndex: 0,
      originalPipelineName: $scope.pipeline.name,
      saving: false,
    };

    $scope.viewState.parallelPipelinesEnabled = !!settings.feature.parallelPipelines;

    this.deletePipeline = function() {
      $modal.open({
        templateUrl: 'scripts/modules/pipelines/config/actions/delete/deletePipelineModal.html',
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
      if (settings.feature.parallelPipelines) {
        $scope.pipeline.stageCounter++;
        newStage.requisiteStageRefIds = [];
        newStage.refId = $scope.pipeline.stageCounter;
        if ($scope.pipeline.stages.length) {
          newStage.requisiteStageRefIds.push($scope.pipeline.stages[$scope.pipeline.stages.length - 1].refId);
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
      var original = angular.fromJson($scope.viewState.original);
      original.name = $scope.pipeline.name;
      $modal.open({
        templateUrl: 'scripts/modules/pipelines/config/actions/rename/renamePipelineModal.html',
        controller: 'RenamePipelineModalCtrl',
        controllerAs: 'renamePipelineModalCtrl',
        resolve: {
          pipeline: function() { return original; },
          application: function() { return $scope.application; }
        }
      }).result.then(function() {
          $scope.pipeline.name = original.name;
          $scope.viewState.original = angular.toJson(original);
        });
    };

    this.editPipelineJson = function() {
      $modal.open({
        templateUrl: 'scripts/modules/pipelines/config/actions/json/editPipelineJsonModal.html',
        controller: 'EditPipelineJsonModalCtrl',
        controllerAs: 'editPipelineJsonModalCtrl',
        resolve: {
          pipeline: function() { return $scope.pipeline; },
        }
      }).result.then(function() {
          $scope.$broadcast('pipeline-json-edited');
        });
    };

    this.navigateToStage = function(index, event) {
      $scope.viewState.section = 'stage';
      $scope.viewState.stageIndex = index;
      if (event && event.target && event.target.focus) {
        event.target.focus();
      }
    };

    this.navigateTo = function(section, index) {
      $scope.viewState.section = section;
      if (section === 'stage') {
        ctrl.navigateToStage(index);
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
        this.navigateTo('settings');
      }
    };

    this.isValid = function() {
      return _.every($scope.pipeline.stages, 'name');
    };

    this.savePipeline = function() {
      var pipeline = $scope.pipeline,
          viewState = $scope.viewState;

      $scope.viewState.saving = true;
      pipelineConfigService.savePipeline(pipeline).then(
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
      $scope.pipeline.stages = original.stages;
      $scope.pipeline.triggers = original.triggers;
      // if we were looking at a stage that no longer exists, move to the last stage
      if ($scope.viewState.section === 'stage') {
        var lastStage = $scope.pipeline.stages.length - 1;
        if ($scope.viewState.stageIndex > lastStage) {
          $scope.viewState.stageIndex = lastStage;
        }
        if (!$scope.pipeline.stages.length) {
          this.navigateTo('triggers');
        }
      }
      $scope.$broadcast('pipeline-reverted');
    };

    function getPlain(pipeline) {
      var base = pipeline.fromServer ? pipeline.plain() : angular.copy(pipeline);
      return {
        stages: base.stages,
        triggers: base.triggers
      };
    }

    function pipelineUpdated(newVal, oldVal) {
      if (newVal && oldVal && newVal.name !== oldVal.name) {
        $scope.viewState.original = null;
      }
      markDirty();
    }

    var markDirty = function markDirty() {
      if (!$scope.viewState.original) {
        $scope.viewState.original = angular.toJson(getPlain($scope.pipeline));
      }
      $scope.viewState.isDirty = $scope.viewState.original !== angular.toJson(getPlain($scope.pipeline));
      if ($scope.viewState.isDirty) {
        dirtyPipelineTracker.add($scope.pipeline.name);
      } else {
        dirtyPipelineTracker.remove($scope.pipeline.name);
      }
    };

    function cacheViewState() {
      var toCache = angular.copy($scope.viewState);
      delete toCache.original;
      configViewStateCache.put(buildCacheKey(), toCache);
    }

    $scope.$watch('pipeline', pipelineUpdated, true);
    $scope.$watch('viewState.original', markDirty, true);
    $scope.$watch('viewState', cacheViewState, true);
    $scope.$watch('pipeline.name', cacheViewState);

  });
