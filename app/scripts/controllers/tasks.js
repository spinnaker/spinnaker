'use strict';


angular.module('deckApp')
  .controller('TasksCtrl', function ($scope, application) {
    $scope.taskStateFilter = 'All';
    $scope.application = application;

  }
);
