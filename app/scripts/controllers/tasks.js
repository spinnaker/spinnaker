'use strict';

angular.module('deckApp')
  .controller('TasksCtrl', function($scope, tasks) {

    $scope.taskStateFilter = 'All';

    $scope.subscribeTo(tasks.observable);

    tasks.create({
      title: 'Testing',
      message: 'A testing task',
    });


  });
