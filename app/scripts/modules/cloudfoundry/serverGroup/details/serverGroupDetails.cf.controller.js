'use strict';

const angular = require('angular');
import _ from 'lodash';

import {
  CONFIRMATION_MODAL_SERVICE,
  SERVER_GROUP_READER,
  SERVER_GROUP_WARNING_MESSAGE_SERVICE,
  SERVER_GROUP_WRITER,
  ServerGroupTemplates,
} from '@spinnaker/core';

module.exports = angular.module('spinnaker.serverGroup.details.cf.controller', [
  require('angular-ui-router').default,
  require('../configure/ServerGroupCommandBuilder.js'),
  SERVER_GROUP_WARNING_MESSAGE_SERVICE,
  SERVER_GROUP_READER,
  CONFIRMATION_MODAL_SERVICE,
  SERVER_GROUP_WRITER,
  require('./resize/resizeServerGroup.controller'),
  require('./rollback/rollbackServerGroup.controller'),
])
    .controller('cfServerGroupDetailsCtrl', function ($scope, $state, $templateCache, $interpolate, app, serverGroup,
                                                      cfServerGroupCommandBuilder, serverGroupReader, $uibModal, confirmationModalService, serverGroupWriter,
                                                      serverGroupWarningMessageService) {

      let application = this.application = app;

      $scope.state = {
        loading: true
      };

      function extractServerGroupSummary() {
        var summary = _.find(application.serverGroups.data, function (toCheck) {
          return toCheck.name === serverGroup.name && toCheck.account === serverGroup.accountId && toCheck.region === serverGroup.region;
        });
        if (!summary) {
          application.loadBalancers.data.some(function (loadBalancer) {
            if (loadBalancer.account === serverGroup.accountId && loadBalancer.region === serverGroup.region) {
              return loadBalancer.serverGroups.some(function (possibleServerGroup) {
                if (possibleServerGroup.name === serverGroup.name) {
                  summary = possibleServerGroup;
                  return true;
                }
              });
            }
          });
        }
        return summary;
      }

      function retrieveServerGroup() {
        var summary = extractServerGroupSummary();
        return serverGroupReader.getServerGroup(application.name, serverGroup.accountId, serverGroup.region, serverGroup.name).then(function(details) {
          cancelLoader();

          // it's possible the summary was not found because the clusters are still loading
          details.account = serverGroup.accountId;
          angular.extend(details, summary);

          $scope.serverGroup = details;

          if (!_.isEmpty($scope.serverGroup)) {
            if (details.securityGroups) {
              $scope.securityGroups = _.chain(details.securityGroups).map(function(id) {
                return _.find(application.securityGroups.data, { 'accountName': serverGroup.accountId, 'region': serverGroup.region, 'id': id }) ||
                    _.find(application.securityGroups.data, { 'accountName': serverGroup.accountId, 'region': serverGroup.region, 'name': id });
              }).compact().value();
            }

            //var pathSegments = $scope.serverGroup.launchConfig.instanceTemplate.selfLink.split('/');
            //var projectId = pathSegments[pathSegments.indexOf('projects') + 1];

            // TODO Add link to CF console outputs

          } else {
            autoClose();
          }
        },
          autoClose
        );
      }

      function autoClose() {
        if ($scope.$$destroyed) {
          return;
        }
        $state.params.allowModalToStayOpen = true;
        $state.go('^', null, {location: 'replace'});
      }

      function cancelLoader() {
        $scope.state.loading = false;
      }

      retrieveServerGroup().then(() => {
        // If the user navigates away from the view before the initial retrieveServerGroup call completes,
        // do not bother subscribing to the refresh
        if (!$scope.$$destroyed) {
          app.serverGroups.onRefresh($scope, retrieveServerGroup);
        }
      });

      this.destroyServerGroup = function destroyServerGroup() {
        var serverGroup = $scope.serverGroup;

        var taskMonitor = {
          application: application,
          title: 'Destroying ' + serverGroup.name,
        };

        var submitMethod = function () {
          return serverGroupWriter.destroyServerGroup(serverGroup, application, {
            cloudProvider: 'cf',
            replicaPoolName: serverGroup.name,
            region: serverGroup.region,
          });
        };

        var stateParams = {
          name: serverGroup.name,
          accountId: serverGroup.account,
          region: serverGroup.region
        };

        const confirmationModalParams = {
          header: 'Really destroy ' + serverGroup.name + '?',
          buttonText: 'Destroy ' + serverGroup.name,
          provider: 'cf',
          account: serverGroup.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
          onTaskComplete: function() {
            if ($state.includes('**.serverGroup', stateParams)) {
              $state.go('^');
            }
          },
        };

        serverGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);

        confirmationModalService.confirm(confirmationModalParams);
      };

      this.disableServerGroup = function disableServerGroup() {
        var serverGroup = $scope.serverGroup;

        var taskMonitor = {
          application: application,
          title: 'Disabling ' + serverGroup.name
        };

        var submitMethod = function () {
          return serverGroupWriter.disableServerGroup(serverGroup, application, {
            cloudProvider: 'cf',
            nativeLoadBalancers: serverGroup.nativeLoadBalancers,
            region: serverGroup.region,
          });
        };

        const confirmationModalParams = {
          header: 'Really disable ' + serverGroup.name + '?',
          buttonText: 'Disable ' + serverGroup.name,
          account: serverGroup.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod
        };

        serverGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, confirmationModalParams);

        confirmationModalService.confirm(confirmationModalParams);

      };

      this.enableServerGroup = function enableServerGroup() {
        var serverGroup = $scope.serverGroup;

        var taskMonitor = {
          application: application,
          title: 'Enabling ' + serverGroup.name,
        };

        var submitMethod = function () {
          return serverGroupWriter.enableServerGroup(serverGroup, application, {
            cloudProvider: 'cf',
            nativeLoadBalancers: serverGroup.nativeLoadBalancers,
            replicaPoolName: serverGroup.name,
            region: serverGroup.region,
          });
        };

        confirmationModalService.confirm({
          header: 'Really enable ' + serverGroup.name + '?',
          buttonText: 'Enable ' + serverGroup.name,
          account: serverGroup.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod
        });

      };

      this.rollbackServerGroup = function rollbackServerGroup() {
        $uibModal.open({
          templateUrl: require('./rollback/rollbackServerGroup.html'),
          controller: 'cfRollbackServerGroupCtrl as ctrl',
          resolve: {
            serverGroup: function() { return $scope.serverGroup; },
            disabledServerGroups: function() {
              var cluster = _.find(app.clusters, {name: $scope.serverGroup.cluster, account: $scope.serverGroup.account});
              return _.filter(cluster.serverGroups, {isDisabled: true, region: $scope.serverGroup.region});
            },
            application: function() { return app; }
          }
        });
      };

      this.resizeServerGroup = function resizeServerGroup() {
        $uibModal.open({
          templateUrl: require('./resize/resizeServerGroup.html'),
          controller: 'cfResizeServerGroupCtrl as ctrl',
          resolve: {
            serverGroup: function() { return $scope.serverGroup; },
            application: function() { return application; }
          }
        });
      };

      this.cloneServerGroup = function cloneServerGroup(serverGroup) {
        $uibModal.open({
          templateUrl: require('../configure/wizard/serverGroupWizard.html'),
          controller: 'cfCloneServerGroupCtrl as ctrl',
          resolve: {
            title: function() { return 'Clone ' + serverGroup.name; },
            application: function() { return application; },
            serverGroup: function() { return serverGroup; },
            serverGroupCommand: function() { return cfServerGroupCommandBuilder.buildServerGroupCommandFromExisting(application, serverGroup); },
          }
        });
      };

      this.showUserData = function showScalingActivities() {
        $scope.userData = window.atob($scope.serverGroup.launchConfig.userData);
        $uibModal.open({
          templateUrl: ServerGroupTemplates.userData,
          controller: 'CloseableModalCtrl',
          scope: $scope
        });
      };

      this.truncateCommitHash = function() {
        if ($scope.serverGroup && $scope.serverGroup.buildInfo && $scope.serverGroup.buildInfo.commit) {
          return $scope.serverGroup.buildInfo.commit.substring(0, 8);
        }
        return null;
      };

      this.getNetwork = function() {
        if ($scope.serverGroup &&
            $scope.serverGroup.launchConfig &&
            $scope.serverGroup.launchConfig.instanceTemplate &&
            $scope.serverGroup.launchConfig.instanceTemplate.properties &&
            $scope.serverGroup.launchConfig.instanceTemplate.properties.networkInterfaces) {
          var networkInterfaces = $scope.serverGroup.launchConfig.instanceTemplate.properties.networkInterfaces;

          if (networkInterfaces.length === 1) {
            var networkUrl = networkInterfaces[0].network;

            return _.last(networkUrl.split('/'));
          }
        }
        return null;
      };

      this.isSecret = function(value) {
        return value.toLowerCase().includes('password') ||
            value.toLowerCase().includes('secret') ||
            value.toLowerCase().includes('key');
      };

    }
);
