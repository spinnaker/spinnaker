'use strict';

const angular = require('angular');
import _ from 'lodash';

import {
  CONFIRMATION_MODAL_SERVICE,
  ServerGroupReader,
  ServerGroupWarningMessageService,
  SERVER_GROUP_WRITER,
  ServerGroupTemplates,
} from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.serverGroup.details.kubernetes.controller', [
    require('@uirouter/angularjs').default,
    require('../configure/configure.kubernetes.module').name,
    CONFIRMATION_MODAL_SERVICE,
    SERVER_GROUP_WRITER,
    require('../paramsMixin').name,
  ])
  .controller('kubernetesServerGroupDetailsController', ['$scope', '$state', 'app', 'serverGroup', '$uibModal', 'serverGroupWriter', 'kubernetesServerGroupCommandBuilder', 'kubernetesServerGroupParamsMixin', 'confirmationModalService', 'kubernetesProxyUiService', function(
    $scope,
    $state,
    app,
    serverGroup,
    $uibModal,
    serverGroupWriter,
    kubernetesServerGroupCommandBuilder,
    kubernetesServerGroupParamsMixin,
    confirmationModalService,
    kubernetesProxyUiService,
  ) {
    let application = (this.application = app);

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
      return kubernetesProxyUiService.buildLink(
        $scope.serverGroup.account,
        $scope.serverGroup.kind,
        $scope.serverGroup.region,
        $scope.serverGroup.name,
      );
    };

    this.showYaml = function showYaml() {
      $scope.userDataModalTitle = 'Replication Controller YAML';
      $scope.userData = $scope.serverGroup.yaml;
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
          angular.extend(params, kubernetesServerGroupParamsMixin.destroyServerGroup(serverGroup, application)),
        );

      var stateParams = {
        name: serverGroup.name,
        accountId: serverGroup.account,
        namespace: serverGroup.namespace,
      };

      const confirmationModalParams = {
        header: 'Really destroy ' + serverGroup.name + '?',
        buttonText: 'Destroy ' + serverGroup.name,
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
        askForReason: true,
        onTaskComplete: () => {
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
          angular.extend(params, kubernetesServerGroupParamsMixin.disableServerGroup(serverGroup, application)),
        );

      var confirmationModalParams = {
        header: 'Really disable ' + serverGroup.name + '?',
        buttonText: 'Disable ' + serverGroup.name,
        provider: 'kubernetes',
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
        askForReason: true,
      };

      ServerGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, confirmationModalParams);

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.enableServerGroup = function enableServerGroup() {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: application,
        title: 'Enabling ' + serverGroup.name,
      };

      var submitMethod = params =>
        serverGroupWriter.enableServerGroup(
          serverGroup,
          application,
          angular.extend(params, kubernetesServerGroupParamsMixin.enableServerGroup(serverGroup, application)),
        );

      var confirmationModalParams = {
        header: 'Really enable ' + serverGroup.name + '?',
        buttonText: 'Enable ' + serverGroup.name,
        provider: 'kubernetes',
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
        askForReason: true,
      };

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.rollbackServerGroup = function rollbackServerGroup() {
      $uibModal.open({
        templateUrl: require('./rollback/rollback.html'),
        controller: 'kubernetesRollbackServerGroupController as ctrl',
        resolve: {
          serverGroup: function() {
            return $scope.serverGroup;
          },
          disabledServerGroups: function() {
            var cluster = _.find(app.clusters, {
              name: $scope.serverGroup.cluster,
              account: $scope.serverGroup.account,
            });
            return _.filter(cluster.serverGroups, { isDisabled: true, region: $scope.serverGroup.namespace });
          },
          application: function() {
            return app;
          },
        },
      });
    };

    this.resizeServerGroup = function resizeServerGroup() {
      $uibModal.open({
        templateUrl: require('./resize/resize.html'),
        controller: 'kubernetesResizeServerGroupController as ctrl',
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
        controller: 'kubernetesCloneServerGroupController as ctrl',
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
            return kubernetesServerGroupCommandBuilder.buildServerGroupCommandFromExisting(application, serverGroup);
          },
        },
      });
    };
  }]);
