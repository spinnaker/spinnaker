'use strict';


angular.module('deckApp.application.controller', [])
  .controller('ApplicationCtrl', function($scope, application) {
    $scope.application = application;
    $scope.insightTarget = application;
    if (application.notFound) {
      return;
    }

    function countInstances() {
      var serverGroups = application.serverGroups || [];
      return serverGroups
        .reduce(function(total, serverGroup) {
          return serverGroup.instances.length + total;
        }, 0);
    }

    if (countInstances() < 500) {
      application.enableAutoRefresh($scope);
    }
  }
);

