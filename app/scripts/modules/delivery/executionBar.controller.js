'use strict';

angular.module('deckApp.delivery')
  .controller('executionBar', function($scope, d3Service, $filter, momentService, scheduler) {
    var controller = this;
    controller.now = momentService().valueOf();

    var subscription = scheduler.subscribe(function() {
      controller.now = momentService().valueOf();
    });

    $scope.$on('destroy', function() {
      subscription.dispose();
    });
    
    controller.getStageWidth = function(stage) {
      var filtered = $filter('stages')($scope.execution.stages, $scope.filter);
      switch ($scope.filter.stage.scale) {
        case 'fixed':
          return 100 / $scope.filter.stage.max + '%';
        case 'absolute':
          var abs = d3Service.max($scope.executions, function(d) {
            return (d.isRunning ? controller.now : d.endTime) - d.startTime;
          });
          return 100 * (stage.endTime - stage.startTime) / abs + '%';
        case 'relative':
          var rel = (filtered[filtered.length - 1].endTime || controller.now) -
            filtered[0].startTime;
          return 100 * ((stage.endTime || controller.now) - stage.startTime) / rel + '%';
        default:
          return '0%';
      }
    };

    controller.getStageColor = function(stage) {
      return $scope.scale[$scope.filter.stage.colorOverlay](
        stage[$scope.filter.stage.colorOverlay].toLowerCase()
      );
    };

    controller.getStageOpacity = function(stage) {
      if (!!$scope.filter.stage.solo.facet &&
          stage[$scope.filter.stage.solo.facet].toLowerCase() !==
            $scope.filter.stage.solo.value) {
        return 0.5;
      } else {
        return 0.8;
      }
    };

    controller.styleStage = function(stage) {
      var style = {
        width: controller.getStageWidth(stage),
        'background-color': controller.getStageColor(stage),
        opacity: controller.getStageOpacity(stage),
      };
      return style;
    };
  });
