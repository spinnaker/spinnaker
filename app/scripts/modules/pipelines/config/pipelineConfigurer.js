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
  .controller('PipelineConfigurerCtrl', function($scope, pipelineConfigService, $modal, $timeout) {

    $scope.viewState = {
      expanded: true,
      section: 'triggers',
      stageIndex: 0,
      originalPipelineName: $scope.pipeline.name,
      saving: false,
    };

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
      $scope.pipeline.stages = $scope.pipeline.stages || [];
      $scope.pipeline.stages.push({ isNew: true });
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
          isCurrentPage = currentPage == startingPagePosition;

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

    this.navigateToStage = function(index) {
      $scope.viewState.section = 'stage';
      $scope.viewState.stageIndex = index;
    };

    this.navigateTo = function(section) {
      $scope.viewState.section = section;
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
      if (stageIndex > 0) {
        $scope.viewState.stageIndex--;
      }
      if (!$scope.pipeline.stages.length) {
        this.navigateTo('settings');
      }
    };

    this.savePipeline = function() {
      var pipeline = $scope.pipeline,
          viewState = $scope.viewState;

      $scope.viewState.saving = true;
      pipelineConfigService.savePipeline(pipeline, viewState.originalPipelineName).then(
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
      $scope.pipeline = angular.fromJson($scope.viewState.original);
      // if we were looking at a stage that no longer exists, move to the last stage
      if ($scope.viewState.section === 'stage') {
        var lastStage = $scope.pipeline.stages.length - 1;
        if ($scope.viewState.stageIndex > lastStage) {
          $scope.viewState.stageIndex = lastStage;
        }
      }
    };

    function getPlain(pipeline) {
      return pipeline.fromServer ? pipeline.plain() : pipeline;
    }

    var markDirty = function markDirty() {
      if (!$scope.viewState.original) {
        $scope.viewState.original = angular.toJson(getPlain($scope.pipeline));
      }
      $scope.viewState.isDirty = $scope.viewState.original !== angular.toJson(getPlain($scope.pipeline));
    };

    $scope.$watch('pipeline', markDirty, true);
    $scope.$watch('viewState.original', markDirty, true);

  });
