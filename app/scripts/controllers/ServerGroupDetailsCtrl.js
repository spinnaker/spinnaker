'use strict';
/* jshint camelcase:false */

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ServerGroupDetailsCtrl', function ($scope, $state, application, serverGroup, orcaService, notifications,
                                                  mortService, oortService, accountService, securityGroupService,
                                                  serverGroupService, $modal, confirmationModalService, _) {

    function extractServerGroup() {
      application.clusters.some(function (cluster) {
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
      if (!$scope.serverGroup) {
        notifications.create({
          message: 'No server group named "' + serverGroup.name + '" was found in ' + serverGroup.accountId + ':' + serverGroup.region,
          autoDismiss: true,
          hideTimestamp: true,
          strong: true
        });
        $state.go('^');
      }
    }

    extractServerGroup();

    application.registerAutoRefreshHandler(extractServerGroup, $scope);

    this.destroyServerGroup = function destroyServerGroup() {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: application,
        title: 'Destroying ' + serverGroup.name,
//        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: false
      };

      var submitMethod = function () {
        return orcaService.destroyServerGroup(serverGroup, application.name);
      };

      var stateParams = {
        name: serverGroup.name,
        accountId: serverGroup.account,
        region: serverGroup.region
      };

      confirmationModalService.confirm({
        header: 'Really destroy ' + serverGroup.name + '?',
        buttonText: 'Destroy ' + serverGroup.name,
        destructive: true,
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
        onTaskComplete: function() {
          if ($state.includes('**.serverGroup', stateParams)) {
            $state.go('^');
          }
        },
        onApplicationRefresh: function() {
          if ($state.includes('**.serverGroup', stateParams)) {
            $state.go('^');
          }
        }
      });
    };

    this.disableServerGroup = function disableServerGroup() {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: application,
        title: 'Disabling ' + serverGroup.name,
//        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: false
      };

      var submitMethod = function () {
        return orcaService.disableServerGroup(serverGroup, application.name);
      };

      var stateParams = {
        name: serverGroup.name,
        accountId: serverGroup.account,
        region: serverGroup.region
      };

      confirmationModalService.confirm({
        header: 'Really disable ' + serverGroup.name + '?',
        buttonText: 'Disable ' + serverGroup.name,
        destructive: true,
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
        onTaskComplete: function() {
          if ($state.includes('**.serverGroup', stateParams)) {
            $state.go('^');
          }
        },
        onApplicationRefresh: function() {
          if ($state.includes('**.serverGroup', stateParams)) {
            $state.go('^');
          }
        }
      });

    };

    this.enableServerGroup = function enableServerGroup() {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: application,
        title: 'Disabling ' + serverGroup.name,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true
      };

      var submitMethod = function () {
        return orcaService.enableServerGroup(serverGroup, application.name);
      };

      confirmationModalService.confirm({
        header: 'Really enable ' + serverGroup.name + '?',
        buttonText: 'Enable ' + serverGroup.name,
        destructive: false,
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
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
        templateUrl: 'views/application/modal/serverGroup/aws/serverGroupWizard.html',
        controller: 'awsCloneServerGroupCtrl as ctrl',
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

    this.buildJenkinsLink = function() {
      if ($scope.serverGroup && $scope.serverGroup.buildInfo && $scope.serverGroup.buildInfo.jenkins) {
        var jenkins = $scope.serverGroup.buildInfo.jenkins;
        return jenkins.host + 'job/' + jenkins.name + '/' + jenkins.number;
      }
      return null;
    };
  }
).controller('ScalingActivitiesCtrl', function($scope) {
  $scope.isSuccessful = function(activity) {
    return activity.statusCode === 'Successful';
  };
});
