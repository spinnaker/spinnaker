'use strict';
/* jshint camelcase:false */

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ServerGroupDetailsCtrl', function ($scope, application, serverGroup, orcaService,
                                                  mortService, oortService, accountService, securityGroupService,
                                                  serverGroupService, $modal, confirmationModalService, _) {

    function extractServerGroup(clusters) {
      clusters.some(function (cluster) {
        return cluster.serverGroups.some(function (toCheck) {
          if (toCheck.name === serverGroup.name && toCheck.account === serverGroup.accountId && toCheck.region === serverGroup.region) {
            $scope.serverGroup = toCheck;
            $scope.cluster = cluster;
            $scope.account = serverGroup.accountId;
            if (toCheck.launchConfig) {
              var launchConfig = angular.copy(toCheck.launchConfig);
              $scope.securityGroups = _.map(launchConfig.securityGroups, function(id) {
                return _.find(application.securityGroups, { 'accountName': toCheck.account, 'region': toCheck.region, 'id': id });
              });
              delete launchConfig.createdTime;
              delete launchConfig.userData;
              delete launchConfig.securityGroups;
              $scope.launchConfig = launchConfig;
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
        templateUrl: 'views/application/modal/serverGroup/resizeServerGroup.html',
        controller: 'ResizeServerGroupCtrl as ctrl',
        resolve: {
          serverGroup: function() { return $scope.serverGroup; },
          application: function() { return application; }
        }
      });
    };

    this.cloneServerGroup = function cloneServerGroup(serverGroup) {
      $modal.open({
        templateUrl: 'views/modal/asgWizard.html',
        controller: 'CloneServerGroupCtrl as ctrl',
        resolve: {
          title: function() { return 'Clone ' + serverGroup.name; },
          application: function() { return application; },
          serverGroup: function() { return serverGroup; }
        }
      });
    };

    this.showScalingActivities = function showScalingActivities() {
      $scope.activities = [];
      var modal = $modal.open({
        templateUrl: 'views/application/modal/serverGroup/scalingActivities.html',
        controller: 'ScalingActivitiesCtrl as ctrl',
        scope: $scope
      });
      modal.opened.then(function() {
        serverGroupService.getScalingActivities(application, $scope.account, $scope.cluster.name, $scope.serverGroup.name, $scope.serverGroup.region).then(function(response) {
          $scope.activities = response;
        });
      });
    };
  }
).controller('ScalingActivitiesCtrl', function($scope) {
  $scope.isSuccessful = function(activity) {
    return activity.statusCode === 'Successful';
  };
});
