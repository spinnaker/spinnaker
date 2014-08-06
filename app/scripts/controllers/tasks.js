'use strict';

module.exports = function($scope, tasks, $log) {

  $scope.taskStateFilter = 'All';

  $log.debug('tasks:', tasks);

  //$scope.subscribeTo(tasks.all);

  $scope.subscribed = {
    data: tasks,
  };

};
