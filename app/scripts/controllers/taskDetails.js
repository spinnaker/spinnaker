'use strict';

var angular = require('angular');

angular.module('deckApp')
  .controller('TaskDetailsCtrl', function($scope, task) {
    $scope.task = task;
  });
