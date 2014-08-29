'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ApplicationsCtrl', function($scope, $exceptionHandler, $modal, $log, $filter, RxService, front50, notifications, oortService) {

    $scope.applicationsLoaded = false;

    $scope.sorting = {
      sortKey: 'name',
      reverse: false
    };

    this.demoMultiPageModal = function() {
      $modal.open(
        {
          templateUrl: 'views/modal/multiPageWrapper.html',
          scope: $scope
        }
      );
    };

    $scope.applicationFilter = '';

    $scope.menuActions = [
      {
        displayName: 'Create Application',
        action: function() {
          $modal.open({
            templateUrl: 'views/newapplication.html'
            // TODO: how to create a new scope
          }).result.then(function(app) {
            front50.all('applications').post(app).then(function(resp) {
              $log.debug(resp);
              notifications.observableTask({
                title: 'Creating application ' + app.name,
                message: app.name + ' Created!',
                observable: $scope.applications.flatMap(function(applications) {
                  return RxService.Observable.fromArray(applications)
                  .filter(function(other) {
                    return other.name === app.name;
                  });
                })
              });
            }, function(err) {
              $exceptionHandler(err);
            });
          });
        }
      }
    ];

    oortService.listApplications().then(function(applications) {
      $scope.applications = applications;
      $scope.applicationsLoaded = true;
    });

  }
);
