'use strict';
/* jshint camelcase:false */


angular.module('deckApp.serverGroup.details.gce.controller', [
  'ui.router',
  'ui.bootstrap',
  'deckApp.notifications.service',
  'deckApp.gce.serverGroupCommandBuilder.service',
  'deckApp.serverGroup.read.service',
  'deckApp.confirmationModal.service',
  'deckApp.serverGroup.write.service',
])
  .controller('gceServerGroupDetailsCtrl', function ($scope, $state, application, serverGroup, notificationsService,
                                                  gceServerGroupCommandBuilder, serverGroupReader, $modal, confirmationModalService, _, serverGroupWriter) {

    $scope.state = {
      loading: true
    };

    function extractServerGroupSummary() {
      var found = application.serverGroups.filter(function (toCheck) {
        return toCheck.name === serverGroup.name && toCheck.account === serverGroup.accountId && toCheck.region === serverGroup.region;
      });
      return found ? found[0] : null;
    }

    function retrieveServerGroup() {
      var summary = extractServerGroupSummary();
      serverGroupReader.getServerGroup(application.name, serverGroup.accountId, serverGroup.region, serverGroup.name).then(function(details) {
        cancelLoader();

        var restangularlessDetails = details.plain();
        angular.extend(restangularlessDetails, summary);

        $scope.serverGroup = restangularlessDetails;
        if (details.launchConfig && details.launchConfig.securityGroups) {
          $scope.securityGroups = _(details.launchConfig.securityGroups).map(function(id) {
            return _.find(application.securityGroups, { 'accountName': serverGroup.accountId, 'region': serverGroup.region, 'id': id }) ||
              _.find(application.securityGroups, { 'accountName': serverGroup.accountId, 'region': serverGroup.region, 'name': id });
          }).compact().value();
        }

        if (_.isEmpty($scope.serverGroup)) {
          notificationsService.create({
            message: 'No server group named "' + serverGroup.name + '" was found in ' + serverGroup.accountId + ':' + serverGroup.region,
            autoDismiss: true,
            hideTimestamp: true,
            strong: true
          });
          $state.go('^');
        }
      });
    }

    function cancelLoader() {
      $scope.state.loading = false;
    }

    retrieveServerGroup();

    application.registerAutoRefreshHandler(retrieveServerGroup, $scope);

    this.destroyServerGroup = function destroyServerGroup() {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: application,
        title: 'Destroying ' + serverGroup.name,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true,
        katoPhaseToMonitor: 'DESTROY_ASG'
      };

      var submitMethod = function () {
        return serverGroupWriter.destroyServerGroup(serverGroup, application);
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
        provider: 'gce',
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
        title: 'Disabling ' + serverGroup.name
      };

      var submitMethod = function () {
        return serverGroupWriter.disableServerGroup(serverGroup, application);
      };

      confirmationModalService.confirm({
        header: 'Really disable ' + serverGroup.name + '?',
        buttonText: 'Disable ' + serverGroup.name,
        destructive: true,
        provider: 'gce',
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });

    };

    this.enableServerGroup = function enableServerGroup() {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: application,
        title: 'Enabling ' + serverGroup.name,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true
      };

      var submitMethod = function () {
        return serverGroupWriter.enableServerGroup(serverGroup, application);
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
        templateUrl: 'scripts/modules/serverGroups/details/resizeServerGroup.html',
        controller: 'ResizeServerGroupCtrl as ctrl',
        resolve: {
          serverGroup: function() { return $scope.serverGroup; },
          application: function() { return application; }
        }
      });
    };

    this.cloneServerGroup = function cloneServerGroup(serverGroup) {
      $modal.open({
        templateUrl: 'scripts/modules/serverGroups/configure/' + serverGroup.type + '/wizard/serverGroupWizard.html',
        controller: serverGroup.type + 'CloneServerGroupCtrl as ctrl',
        resolve: {
          title: function() { return 'Clone ' + serverGroup.name; },
          application: function() { return application; },
          serverGroup: function() { return serverGroup; },
          serverGroupCommand: function() { return gceServerGroupCommandBuilder.buildServerGroupCommandFromExisting(application, serverGroup); },
        }
      });
    };

    this.showScalingActivities = function showScalingActivities() {
      $scope.activities = [];
      $modal.open({
        templateUrl: 'scripts/modules/serverGroups/details/scalingActivities.html',
        controller: 'ScalingActivitiesCtrl as ctrl',
        resolve: {
          applicationName: function() { return application.name; },
          account: function() { return $scope.serverGroup.account; },
          clusterName: function() { return $scope.serverGroup.cluster; },
          serverGroup: function() { return $scope.serverGroup; }
        }
      });
    };

    this.showUserData = function showScalingActivities() {
      $scope.userData = window.atob($scope.serverGroup.launchConfig.userData);
      $modal.open({
        templateUrl: 'views/application/modal/serverGroup/userData.html',
        controller: 'CloseableModalCtrl',
        scope: $scope
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
);
