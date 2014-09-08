'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ServerGroupDetailsCtrl', function ($scope, application, serverGroup, orcaService, $modal, confirmationModalService) {

    function extractServerGroup(clusters) {
      clusters.some(function (cluster) {
        return cluster.serverGroups.some(function (toCheck) {
          if (toCheck.name === serverGroup.name && toCheck.account === serverGroup.accountId && toCheck.region === serverGroup.region) {
            $scope.serverGroup = toCheck;
            $scope.cluster = cluster;
            $scope.account = serverGroup.accountId;
            if (toCheck.launchConfig && toCheck.instances.length) {
              $scope.securityGroups = toCheck.instances[0].securityGroups;
              delete toCheck.launchConfig.createdTime;
              delete toCheck.launchConfig.userData;
            }
            return true;
          }
        });
      });
    }

    extractServerGroup(application.clusters);

    this.destroyServerGroup = function destroyServerGroup() {
      var serverGroup = $scope.serverGroup;
      confirmationModalService.confirm({
        header: 'Really destroy ' + serverGroup.name + '?',
        buttonText: 'Destroy ' + serverGroup.name,
        destructive: true,
        account: serverGroup.account
      }).then(function () {
        orcaService.destroyServerGroup(serverGroup, application.name).then(function (response) {
          console.warn('task: ', response.ref);
        });
      });
    };

    this.disableServerGroup = function disableServerGroup() {
      var serverGroup = $scope.serverGroup;
      confirmationModalService.confirm({
        header: 'Really disable ' + serverGroup.name + '?',
        buttonText: 'Disable ' + serverGroup.name,
        destructive: true,
        account: serverGroup.account
      }).then(function () {
        orcaService.disableServerGroup(serverGroup, application.name).then(function (response) {
          console.warn('task: ', response.ref);
        });
      });
    };

    this.enableServerGroup = function enableServerGroup() {
      var serverGroup = $scope.serverGroup;
      confirmationModalService.confirm({
        header: 'Really enable ' + serverGroup.name + '?',
        buttonText: 'Enable ' + serverGroup.name,
        destructive: false,
        account: serverGroup.account
      }).then(function () {
        orcaService.enableServerGroup(serverGroup, application.name).then(function (response) {
          console.warn('task: ', response.ref);
        });
      });
    };

    this.resizeServerGroup = function resizeServerGroup() {
      $modal.open({
        templateUrl: 'views/application/modal/resizeServerGroup.html',
        controller: 'ResizeServerGroupCtrl as ctrl',
        resolve: {
          serverGroup: function() { return $scope.serverGroup; },
          application: function() { return application; }
        }
      });
    };

  }
);
