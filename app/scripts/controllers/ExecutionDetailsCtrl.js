'use strict';

angular.module('deckApp')
  .controller('ExecutionDetailsCtrl', function($scope, pipelines) {
    $scope.execution = pipelines.getOne();

    var subscription = pipelines.subscribe(function(e) {
      $scope.execution = e;
    });

    $scope.$on('$destroy', function() {
      subscription.dispose();
    });

  });
