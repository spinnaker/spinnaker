'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';
import _ from 'lodash';

import {
  ConfirmationModalService,
  FirewallLabels,
  SERVER_GROUP_WRITER,
  ServerGroupReader,
  ServerGroupWarningMessageService,
} from '@spinnaker/core';

import '../configure/serverGroup.configure.azure.module';
import { AZURE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE } from '../configure/serverGroupCommandBuilder.service';
import { AzureRollbackServerGroupModal } from './rollback/RollbackServerGroupModal';

export const AZURE_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_AZURE_CONTROLLER =
  'spinnaker.azure.serverGroup.details.controller';
export const name = AZURE_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_AZURE_CONTROLLER; // for backwards compatibility
angular
  .module(AZURE_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_AZURE_CONTROLLER, [
    UIROUTER_ANGULARJS,
    AZURE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE,
    SERVER_GROUP_WRITER,
  ])
  .controller('azureServerGroupDetailsCtrl', [
    '$scope',
    '$state',
    '$templateCache',
    'app',
    'serverGroup',
    'azureServerGroupCommandBuilder',
    '$uibModal',
    'serverGroupWriter',
    function (
      $scope,
      $state,
      $templateCache,
      app,
      serverGroup,
      azureServerGroupCommandBuilder,
      $uibModal,
      serverGroupWriter,
    ) {
      $scope.state = {
        loading: true,
      };

      $scope.firewallsLabel = FirewallLabels.get('Firewalls');

      this.application = app;

      function extractServerGroupSummary() {
        let summary = _.find(app.serverGroups.data, function (toCheck) {
          return (
            toCheck.name === serverGroup.name &&
            toCheck.account === serverGroup.accountId &&
            toCheck.region === serverGroup.region
          );
        });
        if (!summary) {
          app.loadBalancers.data.some(function (loadBalancer) {
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
        if (!summary) {
          $state.go('^');
        }
        return summary;
      }

      function retrieveServerGroup() {
        const summary = extractServerGroupSummary();
        return ServerGroupReader.getServerGroup(
          app.name,
          serverGroup.accountId,
          serverGroup.region,
          serverGroup.name,
        ).then(function (details) {
          cancelLoader();

          angular.extend(details, summary);
          details.account = serverGroup.accountId; // it's possible the summary was not found because the clusters are still loading

          $scope.serverGroup = details;

          if (!_.isEmpty($scope.serverGroup)) {
            $scope.image = details.image ? details.image : undefined;

            if (details.image && details.image.description) {
              const tags = details.image.description.split(', ');
              tags.forEach(function (tag) {
                const keyVal = tag.split('=');
                if (keyVal.length === 2 && keyVal[0] === 'ancestor_name') {
                  details.image.baseImage = keyVal[1];
                }
              });
            }

            if (details.launchConfig && details.launchConfig.securityGroups) {
              $scope.securityGroups = _.chain(details.launchConfig.securityGroups)
                .map(function (id) {
                  return (
                    _.find(app.securityGroups.data, {
                      accountName: serverGroup.accountId,
                      region: serverGroup.region,
                      id: id,
                    }) ||
                    _.find(app.securityGroups.data, {
                      accountName: serverGroup.accountId,
                      region: serverGroup.region,
                      name: id,
                    })
                  );
                })
                .compact()
                .value();
            }
          } else {
            $state.go('^');
          }
        });
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
        const serverGroup = $scope.serverGroup;

        const stateParams = {
          name: serverGroup.name,
          accountId: serverGroup.account,
          region: serverGroup.region,
        };

        const taskMonitor = {
          application: app,
          title: 'Destroying ' + serverGroup.name,
          onTaskComplete: function () {
            if ($state.includes('**.serverGroup', stateParams)) {
              $state.go('^');
            }
          },
        };

        const submitMethod = function () {
          return serverGroupWriter.destroyServerGroup(serverGroup, app);
        };

        const confirmationModalParams = {
          header: 'Really destroy ' + serverGroup.name + '?',
          buttonText: 'Destroy ' + serverGroup.name,
          account: serverGroup.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        };

        ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);

        ConfirmationModalService.confirm(confirmationModalParams);
      };

      this.disableServerGroup = function disableServerGroup() {
        const serverGroup = $scope.serverGroup;

        const taskMonitor = {
          application: app,
          title: 'Disabling ' + serverGroup.name,
        };

        const submitMethod = () => serverGroupWriter.disableServerGroup(serverGroup, app);

        const confirmationModalParams = {
          header: 'Really disable ' + serverGroup.name + '?',
          buttonText: 'Disable ' + serverGroup.name,
          account: serverGroup.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        };

        ServerGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, confirmationModalParams);

        ConfirmationModalService.confirm(confirmationModalParams);
      };

      this.enableServerGroup = function enableServerGroup() {
        const serverGroup = $scope.serverGroup;

        const taskMonitor = {
          application: app,
          title: 'Enabling ' + serverGroup.name,
        };

        const submitMethod = (params) => {
          return serverGroupWriter.enableServerGroup(
            serverGroup,
            app,
            angular.extend(params, {
              interestingHealthProviderNames: [], // bypass the check for now; will change this later to ['azureService']
            }),
          );
        };

        ConfirmationModalService.confirm({
          header: 'Really enable ' + serverGroup.name + '?',
          buttonText: 'Enable ' + serverGroup.name,
          account: serverGroup.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };

      this.rollbackServerGroup = () => {
        const serverGroup = $scope.serverGroup;
        const cluster = _.find(app.clusters, { name: serverGroup.cluster, account: serverGroup.account });
        const disabledServerGroups = _.filter(cluster.serverGroups, { isDisabled: true, region: serverGroup.region });
        AzureRollbackServerGroupModal.show({ application: app, serverGroup, disabledServerGroups });
      };

      this.cloneServerGroup = (serverGroup) => {
        $uibModal.open({
          templateUrl: require('../configure/wizard/serverGroupWizard.html'),
          controller: 'azureCloneServerGroupCtrl as ctrl',
          size: 'lg',
          resolve: {
            title: () => 'Clone ' + serverGroup.name,
            application: () => app,
            serverGroupCommand: () =>
              azureServerGroupCommandBuilder.buildServerGroupCommandFromExisting(app, serverGroup),
          },
        });
      };

      this.truncateCommitHash = function () {
        if ($scope.serverGroup && $scope.serverGroup.buildInfo && $scope.serverGroup.buildInfo.commit) {
          return $scope.serverGroup.buildInfo.commit.substring(0, 8);
        }
        return null;
      };
    },
  ]);
