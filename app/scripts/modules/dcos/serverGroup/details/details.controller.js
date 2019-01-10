'use strict';

const angular = require('angular');
import _ from 'lodash';

import {
  CONFIRMATION_MODAL_SERVICE,
  ServerGroupWarningMessageService,
  ServerGroupReader,
  SERVER_GROUP_WRITER,
  ServerGroupTemplates,
} from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.dcos.serverGroup.details.controller', [
    require('../configure/configure.dcos.module.js').name,
    CONFIRMATION_MODAL_SERVICE,
    SERVER_GROUP_WRITER,
    require('../paramsMixin.js').name,
  ])
  .controller('dcosServerGroupDetailsController', function(
    $scope,
    $state,
    app,
    serverGroup,
    $uibModal,
    serverGroupWriter,
    dcosServerGroupCommandBuilder,
    dcosServerGroupParamsMixin,
    confirmationModalService,
    dcosProxyUiService,
  ) {
    let application = app;

    $scope.state = {
      loading: true,
    };

    function extractServerGroupSummary() {
      var summary = _.find(application.serverGroups.data, function(toCheck) {
        return (
          toCheck.name === serverGroup.name &&
          toCheck.account === serverGroup.accountId &&
          toCheck.region === serverGroup.region
        );
      });
      if (!summary) {
        application.loadBalancers.data.some(function(loadBalancer) {
          if (loadBalancer.account === serverGroup.accountId && loadBalancer.region === serverGroup.region) {
            return loadBalancer.serverGroups.some(function(possibleServerGroup) {
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

    this.uiLink = function uiLink() {
      return dcosProxyUiService.buildLink(
        $scope.serverGroup.clusterUrl,
        $scope.serverGroup.account,
        $scope.serverGroup.region,
        $scope.serverGroup.name,
      );
    };

    this.showJson = function showJson() {
      $scope.userDataModalTitle = 'Application JSON';
      $scope.userData = $scope.serverGroup.json;
      $uibModal.open({
        templateUrl: ServerGroupTemplates.userData,
        scope: $scope,
      });
    };

    function normalizeDeploymentStatus(serverGroup) {
      let deploymentStatus = serverGroup.deploymentStatus;

      if (deploymentStatus !== undefined && deploymentStatus !== null) {
        deploymentStatus.unavailableReplicas |= 0;
        deploymentStatus.availableReplicas |= 0;
        deploymentStatus.updatedReplicas |= 0;
      }
    }

    function retrieveServerGroup() {
      var summary = extractServerGroupSummary();
      return ServerGroupReader.getServerGroup(
        application.name,
        serverGroup.accountId,
        serverGroup.region,
        serverGroup.name,
      ).then(function(details) {
        cancelLoader();

        angular.extend(details, summary);

        $scope.serverGroup = details;
        normalizeDeploymentStatus($scope.serverGroup);
      }, autoClose);
    }

    function autoClose() {
      if ($scope.$$destroyed) {
        return;
      }
      $state.go('^', { allowModalToStayOpen: true }, { location: 'replace' });
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

    this.isLastServerGroupInRegion = function(serverGroup, application) {
      try {
        var cluster = _.find(application.clusters, { name: serverGroup.cluster, account: serverGroup.account });
        return _.filter(cluster.serverGroups, { region: serverGroup.region }).length === 1;
      } catch (error) {
        return false;
      }
    };

    this.destroyServerGroup = () => {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: application,
        title: 'Destroying ' + serverGroup.name,
      };

      var submitMethod = params =>
        serverGroupWriter.destroyServerGroup(
          serverGroup,
          application,
          angular.extend(params, dcosServerGroupParamsMixin.destroyServerGroup(serverGroup, application)),
        );

      var stateParams = {
        name: serverGroup.name,
        accountId: serverGroup.account,
        region: serverGroup.region,
      };

      var confirmationModalParams = {
        header: 'Really destroy ' + serverGroup.name + '?',
        buttonText: 'Destroy ' + serverGroup.name,
        provider: 'dcos',
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        platformHealthType: 'DCOS',
        submitMethod: submitMethod,
        onTaskComplete: function() {
          if ($state.includes('**.serverGroup', stateParams)) {
            $state.go('^');
          }
        },
      };

      ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.disableServerGroup = function disableServerGroup() {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: application,
        title: 'Disabling ' + serverGroup.name,
      };

      var submitMethod = params =>
        serverGroupWriter.disableServerGroup(
          serverGroup,
          application,
          angular.extend(params, dcosServerGroupParamsMixin.disableServerGroup(serverGroup, application)),
        );

      var confirmationModalParams = {
        header: 'Really disable ' + serverGroup.name + '?',
        buttonText: 'Disable ' + serverGroup.name,
        provider: 'dcos',
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
        askForReason: true,
      };

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.resizeServerGroup = function resizeServerGroup() {
      $uibModal.open({
        templateUrl: require('./resize/resize.html'),
        controller: 'dcosResizeServerGroupController as ctrl',
        resolve: {
          serverGroup: function() {
            return $scope.serverGroup;
          },
          application: function() {
            return application;
          },
        },
      });
    };

    this.cloneServerGroup = function cloneServerGroup(serverGroup) {
      $uibModal.open({
        templateUrl: require('../configure/wizard/wizard.html'),
        controller: 'dcosCloneServerGroupController as ctrl',
        size: 'lg',
        resolve: {
          title: function() {
            return 'Clone ' + serverGroup.name;
          },
          application: function() {
            return application;
          },
          serverGroup: function() {
            return serverGroup;
          },
          serverGroupCommand: function() {
            return dcosServerGroupCommandBuilder.buildServerGroupCommandFromExisting(application, serverGroup);
          },
        },
      });
    };
  });
