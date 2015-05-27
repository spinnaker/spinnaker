'use strict';


angular.module('spinnaker.application.controller', [])
  .controller('ApplicationCtrl', function($scope, application) {
    $scope.application = application;
    $scope.insightTarget = application;
    if (application.notFound) {
      return;
    }

    this.toggleRefresh = function() {
      if (application.autoRefreshEnabled) {
        application.disableAutoRefresh();
      } else {
        application.resumeAutoRefresh();
        application.refreshImmediately();
      }
    };

    application.enableAutoRefresh($scope);
  }
);

