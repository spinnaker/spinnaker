'use strict';


angular.module('deckApp.application.controller', [])
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

    function countInstances() {
      var serverGroups = application.serverGroups || [];
      return serverGroups
        .reduce(function(total, serverGroup) {
          return serverGroup.instances.length + total;
        }, 0);
    }

    application.enableAutoRefresh($scope);
    if (countInstances() > 500) {
      application.disableAutoRefresh();
    }
  }
);

