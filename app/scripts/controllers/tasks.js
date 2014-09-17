'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('TasksCtrl', function ($scope, application) {
    $scope.taskStateFilter = 'All';

    $scope.subscribed = {
      data: application.tasks,
    };

  }
);
