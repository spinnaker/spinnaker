'use strict';

angular.module('deckApp')
  .controller('TasksCtrl', function($scope, tasks) {

    $scope.taskStateFilter = 'All';

    //$scope.subscribeTo(tasks.all);

    $scope.subscribed = {
      data: [
        {
          description: 'Deploy Oort',
          status: 'STARTED',
          startTime: 1406145224827,
          endTime: 1406146059646,
          steps: [
            {
              name: "orca-config-step",
              status: "COMPLETED",
              startTime: 1406145224849,
              endTime: 1406145224896
            },
            {
              name: "CreateBakeStep",
              status: "COMPLETED",
              startTime: 1406145224908,
              endTime: 1406145229845
            },
            {
              name: "MonitorBakeStep",
              status: "COMPLETED",
              startTime: 1406145229862,
              endTime: 1406145448532
            },
            {
              name: "CompletedBakeStep",
              status: "COMPLETED",
              startTime: 1406145448542,
              endTime: 1406145448846
            },
            {
              name: "CreateDeployStep",
              status: "COMPLETED",
              startTime: 1406145448855,
              endTime: 1406145449409
            },
            {
              name: "MonitorDeployStep",
              status: "COMPLETED",
              startTime: 1406145449419,
              endTime: 1406145459259
            },
            {
              name: "WaitForUpInstancesStep",
              status: "FAILED",
              startTime: 1406145459269,
              endTime: 1406146059642
            }
          ],
        }
      ]
    };

  });
