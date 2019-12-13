'use strict';

import * as angular from 'angular';
import _ from 'lodash';

import {
  CONFIRMATION_MODAL_SERVICE,
  ServerGroupWarningMessageService,
  ServerGroupReader,
  SERVER_GROUP_WRITER,
  ServerGroupTemplates,
} from '@spinnaker/core';
import { DCOS_SERVERGROUP_CONFIGURE_CONFIGURE_DCOS_MODULE } from '../configure/configure.dcos.module';
import { DCOS_SERVERGROUP_PARAMSMIXIN } from '../paramsMixin';

export const DCOS_SERVERGROUP_DETAILS_DETAILS_CONTROLLER = 'spinnaker.dcos.serverGroup.details.controller';
export const name = DCOS_SERVERGROUP_DETAILS_DETAILS_CONTROLLER; // for backwards compatibility
angular
  .module(DCOS_SERVERGROUP_DETAILS_DETAILS_CONTROLLER, [
    DCOS_SERVERGROUP_CONFIGURE_CONFIGURE_DCOS_MODULE,
    CONFIRMATION_MODAL_SERVICE,
    SERVER_GROUP_WRITER,
    DCOS_SERVERGROUP_PARAMSMIXIN,
  ])
  .controller('dcosServerGroupDetailsController', [
    '$scope',
    '$state',
    'app',
    'serverGroup',
    '$uibModal',
    'serverGroupWriter',
    'dcosServerGroupCommandBuilder',
    'dcosServerGroupParamsMixin',
    'confirmationModalService',
    'dcosProxyUiService',
    function(
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
      const application = app;

      $scope.state = {
        loading: true,
      };

      function extractServerGroupSummary() {
        let summary = _.find(application.serverGroups.data, function(toCheck) {
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
        const deploymentStatus = serverGroup.deploymentStatus;

        if (deploymentStatus !== undefined && deploymentStatus !== null) {
          deploymentStatus.unavailableReplicas |= 0;
          deploymentStatus.availableReplicas |= 0;
          deploymentStatus.updatedReplicas |= 0;
        }
      }

      function retrieveServerGroup() {
        const summary = extractServerGroupSummary();
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
          const cluster = _.find(application.clusters, { name: serverGroup.cluster, account: serverGroup.account });
          return _.filter(cluster.serverGroups, { region: serverGroup.region }).length === 1;
        } catch (error) {
          return false;
        }
      };

      this.destroyServerGroup = () => {
        const serverGroup = $scope.serverGroup;

        const taskMonitor = {
          application: application,
          title: 'Destroying ' + serverGroup.name,
        };

        const submitMethod = params =>
          serverGroupWriter.destroyServerGroup(
            serverGroup,
            application,
            angular.extend(params, dcosServerGroupParamsMixin.destroyServerGroup(serverGroup, application)),
          );

        const stateParams = {
          name: serverGroup.name,
          accountId: serverGroup.account,
          region: serverGroup.region,
        };

        const confirmationModalParams = {
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
        const serverGroup = $scope.serverGroup;

        const taskMonitor = {
          application: application,
          title: 'Disabling ' + serverGroup.name,
        };

        const submitMethod = params =>
          serverGroupWriter.disableServerGroup(
            serverGroup,
            application,
            angular.extend(params, dcosServerGroupParamsMixin.disableServerGroup(serverGroup, application)),
          );

        const confirmationModalParams = {
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
    },
  ]);
