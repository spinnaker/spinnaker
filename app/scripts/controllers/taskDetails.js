'use strict';


angular.module('deckApp')
  .controller('TaskDetailsCtrl', function($scope, task) {
    $scope.task = task;
  });
