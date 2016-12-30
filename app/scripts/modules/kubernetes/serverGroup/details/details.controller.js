'use strict';

import _ from 'lodash';
let angular = require('angular');

import {SERVER_GROUP_WARNING_MESSAGE_SERVICE} from 'core/serverGroup/details/serverGroupWarningMessage.service';


module.exports = angular.module('spinnaker.serverGroup.details.kubernetes.controller', [
  require('angular-ui-router'),
  require('../configure/configure.kubernetes.module.js'),
  require('core/confirmationModal/confirmationModal.service.js'),
  require('core/serverGroup/configure/common/runningExecutions.service.js'),
  SERVER_GROUP_WARNING_MESSAGE_SERVICE,
  require('core/serverGroup/serverGroup.read.service.js'),
  require('core/serverGroup/serverGroup.write.service.js'),
  require('core/insight/insightFilterState.model.js'),
  require('core/utils/selectOnDblClick.directive.js'),
  require('../paramsMixin.js'),
])
  .controller('kubernetesServerGroupDetailsController', function ($scope, $state, app, serverGroup, InsightFilterStateModel,
                                                                  serverGroupReader, $uibModal, serverGroupWriter,
                                                                  runningExecutionsService, serverGroupWarningMessageService,
                                                                  kubernetesServerGroupCommandBuilder, kubernetesServerGroupParamsMixin,
                                                                  confirmationModalService) {
    let application = app;

    $scope.state = {
      loading: true
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

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

    this.showYaml = function showYaml() {
      $scope.userDataModalTitle = 'Replication Controller YAML';
      $scope.userData = $scope.serverGroup.yaml;
      $uibModal.open({
        templateUrl: require('core/serverGroup/details/userData.html'),
        controller: 'CloseableModalCtrl',
        scope: $scope
      });
    };

    function normalizeDeploymentStatus(serverGroup) {
      let deploymentStatus = serverGroup.deploymentStatus;

      if (deploymentStatus !== undefined) {
        deploymentStatus.unavailableReplicas |= 0;
        deploymentStatus.availableReplicas |= 0;
        deploymentStatus.updatedReplicas |= 0;
      }
    }

    function retrieveServerGroup() {
      var summary = extractServerGroupSummary();
      return serverGroupReader.getServerGroup(application.name, serverGroup.accountId, serverGroup.region, serverGroup.name).then(function(details) {
        cancelLoader();

        angular.extend(details, summary);

        $scope.serverGroup = details;
        normalizeDeploymentStatus($scope.serverGroup);
        $scope.runningExecutions = function() {
          return runningExecutionsService.filterRunningExecutions($scope.serverGroup.executions);
        };
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

    this.destroyServerGroup = () => {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: application,
        title: 'Destroying ' + serverGroup.name,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true,
        katoPhaseToMonitor: 'DESTROY_ASG'
      };

      var submitMethod = (params) => serverGroupWriter.destroyServerGroup(
          serverGroup,
          application,
          angular.extend(params, kubernetesServerGroupParamsMixin.destroyServerGroup(serverGroup, application))
      );

      var stateParams = {
        name: serverGroup.name,
        accountId: serverGroup.account,
        namespace: serverGroup.namespace
      };

      confirmationModalService.confirm({
        header: 'Really destroy ' + serverGroup.name + '?',
        buttonText: 'Destroy ' + serverGroup.name,
        provider: 'kubernetes',
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
        askForReason: true,
        body: this.getBodyTemplate(serverGroup, application),
        onTaskComplete: () => {
          if ($state.includes('**.serverGroup', stateParams)) {
            $state.go('^');
          }
        },
        onApplicationRefresh: () => {
          if ($state.includes('**.serverGroup', stateParams)) {
            $state.go('^');
          }
        }
      });
    };

    this.getBodyTemplate = (serverGroup, application) => {
      if (this.isLastServerGroupInRegion(serverGroup, application)) {
        return serverGroupWarningMessageService.getMessage(serverGroup);
      }
    };

    this.isLastServerGroupInRegion = function (serverGroup, application ) {
      try {
        var cluster = _.find(application.clusters, {name: serverGroup.cluster, account:serverGroup.account});
        return _.filter(cluster.serverGroups, {region: serverGroup.region}).length === 1;
      } catch (error) {
        return false;
      }
    };

    this.disableServerGroup = function disableServerGroup() {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: application,
        title: 'Disabling ' + serverGroup.name
      };

      var submitMethod = (params) => serverGroupWriter.disableServerGroup(
          serverGroup,
          application,
          angular.extend(params, kubernetesServerGroupParamsMixin.disableServerGroup(serverGroup, application))
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

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.enableServerGroup = function enableServerGroup() {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: application,
        title: 'Enabling ' + serverGroup.name,
        forceRefreshMessage: 'Refreshing application...',
      };

      var submitMethod = (params) => serverGroupWriter.enableServerGroup(
          serverGroup,
          application,
          angular.extend(params, kubernetesServerGroupParamsMixin.enableServerGroup(serverGroup, application))
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
          serverGroup: function() { return $scope.serverGroup; },
          disabledServerGroups: function() {
            var cluster = _.find(app.clusters, {name: $scope.serverGroup.cluster, account: $scope.serverGroup.account});
            return _.filter(cluster.serverGroups, {isDisabled: true, region: $scope.serverGroup.namespace});
          },
          application: function() { return app; }
        }
      });
    };

    this.resizeServerGroup = function resizeServerGroup() {
      $uibModal.open({
        templateUrl: require('./resize/resize.html'),
        controller: 'kubernetesResizeServerGroupController as ctrl',
        resolve: {
          serverGroup: function() { return $scope.serverGroup; },
          application: function() { return application; }
        }
      });
    };

    this.cloneServerGroup = function cloneServerGroup(serverGroup) {
      $uibModal.open({
        templateUrl: require('../configure/wizard/wizard.html'),
        controller: 'kubernetesCloneServerGroupController as ctrl',
        size: 'lg',
        resolve: {
          title: function() { return 'Clone ' + serverGroup.name; },
          application: function() { return application; },
          serverGroup: function() { return serverGroup; },
          serverGroupCommand: function() { return kubernetesServerGroupCommandBuilder.buildServerGroupCommandFromExisting(application, serverGroup); },
        }
      });
    };
  }
);
