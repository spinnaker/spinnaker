'use strict';

angular.module('deckApp.pipelines.stage.executionWindows.directive', [
  'deckApp.pipelines.stage.executionWindows.controller',
])
  .directive('executionWindows', function() {
    return {
      restrict: 'E',
      scope: {
        stage: '='
      },
      templateUrl: 'scripts/modules/pipelines/config/stages/executionWindows/executionWindows.html',
      controller: 'ExecutionWindowsCtrl',
      controllerAs: 'executionWindowsCtrl',
    };
  });
