'use strict';

angular.module('deckApp')
  .controller('ApplicationsCtrl', function($scope, applications, $modal, front50, notifications) {
    $scope.applications = applications.data;

    $scope.menuActions = [
      {
        displayName: 'Create Application',
        action: function() {
          $modal.open({
            templateUrl: 'views/newapplication.html',
            // TODO: how to create a new scope
          }).result.then(function(app) {
            front50.all('applications').post(app).then(function(resp) {
              $log.debug(resp);
              notifications.observableTask({
                title: 'Creating application ' + app.name,
                message: app.name + ' Created!',
                observable: applications.flatMap(function(applications) {
                  return RxService.Observable.fromArray(applications)
                  .filter(function(other) {
                    return other.name === app.name;
                  });
                }),
              });
            }, function(err) {
              $exceptionHandler(err);
            });
          });
        },
      },
    ];

  });
