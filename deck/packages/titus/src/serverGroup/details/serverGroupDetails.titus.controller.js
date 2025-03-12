'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';
import _ from 'lodash';

import {
  AccountService,
  ClusterTargetBuilder,
  ConfirmationModalService,
  confirmNotManaged,
  ReactModal,
  SERVER_GROUP_WRITER,
  ServerGroupReader,
  ServerGroupWarningMessageService,
  SETTINGS,
} from '@spinnaker/core';

import { TITUS_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER } from '../configure/ServerGroupCommandBuilder';
import { TitusCloneServerGroupModal } from '../configure/wizard/TitusCloneServerGroupModal';
import { DISRUPTION_BUDGET_DETAILS_SECTION } from './disruptionBudget/DisruptionBudgetSection';
import { TitusReactInjector } from '../../reactShims';
import { TitusResizeServerGroupModal } from './resize/TitusResizeServerGroupModal';
import { TITUS_SERVERGROUP_DETAILS_ROLLBACK_ROLLBACKSERVERGROUP_CONTROLLER } from './rollback/rollbackServerGroup.controller';
import { SCALING_POLICY_MODULE } from './scalingPolicy/scalingPolicy.module';
import { SERVICE_JOB_PROCESSES_DETAILS_SECTION } from './serviceJobProcesses/ServiceJobProcessesSection';
import { TITUS_PACKAGE_DETAILS_SECTION } from './titusPackageDetailsSection.component';
import { TITUS_SECURITY_GROUPS_DETAILS } from './titusSecurityGroups.component';

export const TITUS_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_TITUS_CONTROLLER =
  'spinnaker.serverGroup.details.titus.controller';
export const name = TITUS_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_TITUS_CONTROLLER; // for backwards compatibility
angular
  .module(TITUS_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_TITUS_CONTROLLER, [
    UIROUTER_ANGULARJS,
    TITUS_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER,
    DISRUPTION_BUDGET_DETAILS_SECTION,
    SERVER_GROUP_WRITER,
    TITUS_SERVERGROUP_DETAILS_ROLLBACK_ROLLBACKSERVERGROUP_CONTROLLER,
    SERVICE_JOB_PROCESSES_DETAILS_SECTION,
    SCALING_POLICY_MODULE,
    TITUS_SECURITY_GROUPS_DETAILS,
    TITUS_PACKAGE_DETAILS_SECTION,
  ])
  .controller('titusServerGroupDetailsCtrl', [
    '$scope',
    '$state',
    '$templateCache',
    '$interpolate',
    'app',
    'serverGroup',
    'titusServerGroupCommandBuilder',
    '$uibModal',
    'serverGroupWriter',
    'awsServerGroupTransformer',
    function (
      $scope,
      $state,
      $templateCache,
      $interpolate,
      app,
      serverGroup,
      titusServerGroupCommandBuilder,
      $uibModal,
      serverGroupWriter,
      awsServerGroupTransformer,
    ) {
      const application = app;
      this.application = app;

      $scope.gateUrl = SETTINGS.gateUrl;

      $scope.state = {
        loading: true,
      };

      function extractServerGroupSummary() {
        const summary = _.find(application.serverGroups.data, function (toCheck) {
          return (
            toCheck.name === serverGroup.name &&
            toCheck.account === serverGroup.accountId &&
            toCheck.region === serverGroup.region
          );
        });
        return summary;
      }

      function retrieveServerGroup() {
        const summary = extractServerGroupSummary();
        return ServerGroupReader.getServerGroup(
          application.name,
          serverGroup.accountId,
          serverGroup.region,
          serverGroup.name,
        ).then(function (details) {
          cancelLoader();

          // it's possible the summary was not found because the clusters are still loading
          details.account = serverGroup.accountId;

          AccountService.getAccountDetails(details.account).then((accountDetails) => {
            details.apiEndpoint = _.filter(accountDetails.regions, { name: details.region })[0].endpoint;
          });

          $scope.buildInfo = details.buildInfo;

          angular.extend(details, summary);

          $scope.serverGroup = details;
          const labels = $scope.serverGroup.labels;
          delete labels['name'];
          delete labels['source'];
          delete labels['spinnakerAccount'];

          delete labels[''];

          Object.keys(labels).forEach((key) => {
            if (key.startsWith('titus.')) {
              delete labels[key];
            }
          });

          $scope.labels = labels;

          transformScalingPolicies(details);

          if (!_.isEmpty($scope.serverGroup)) {
            configureEntityTagTargets();
          } else {
            autoClose();
          }
        }, autoClose);
      }

      function transformScalingPolicies(serverGroup) {
        serverGroup.scalingPolicies = (serverGroup.scalingPolicies || [])
          .map((p) => {
            const { policy } = p;
            const { stepPolicyDescriptor, targetPolicyDescriptor } = policy;
            const policyType = stepPolicyDescriptor ? 'StepScaling' : 'TargetTrackingScaling';
            if (stepPolicyDescriptor) {
              const alarm = stepPolicyDescriptor.alarmConfig;
              alarm.period = alarm.periodSec;
              alarm.namespace = alarm.metricNamespace;
              alarm.disableEditingDimensions = true;
              if (alarm.metricNamespace === 'NFLX/EPIC' && !alarm.dimensions) {
                // NOTE: Titus creates the step scaling policy with these dimensions
                // TODO: Remove this once Titus supports configuring dimensions
                alarm.dimensions = [{ name: 'AutoScalingGroupName', value: serverGroup.name }];
              }
              if (!alarm.dimensions) {
                alarm.dimensions = [];
              }
              const policy = _.cloneDeep(stepPolicyDescriptor.scalingPolicy);
              policy.cooldown = policy.cooldownSec;
              policy.policyType = policyType;
              policy.alarms = [alarm];
              policy.id = p.id;
              if (policy.stepAdjustments) {
                policy.stepAdjustments.forEach((step) => {
                  // gRPC currently returns these values in upper camel case
                  step.metricIntervalUpperBound = _.get(
                    step,
                    'metricIntervalUpperBound',
                    step.MetricIntervalUpperBound,
                  );
                  step.metricIntervalLowerBound = _.get(
                    step,
                    'metricIntervalLowerBound',
                    step.MetricIntervalLowerBound,
                  );
                });
              }
              return policy;
            } else {
              const { customizedMetricSpecification } = targetPolicyDescriptor;
              if (customizedMetricSpecification.dimensions === undefined) {
                customizedMetricSpecification.dimensions = [];
              }
              policy.id = p.id;
              policy.targetTrackingConfiguration = policy.targetPolicyDescriptor;
              policy.targetTrackingConfiguration.scaleOutCooldown =
                policy.targetTrackingConfiguration.scaleOutCooldownSec;
              policy.targetTrackingConfiguration.scaleInCooldown =
                policy.targetTrackingConfiguration.scaleInCooldownSec;
              return policy;
            }
          })
          .map((p) => awsServerGroupTransformer.transformScalingPolicy(p));
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

      retrieveServerGroup()
        .then(() => {
          // If the user navigates away from the view before the initial retrieveServerGroup call completes,
          // do not bother subscribing to the refresh
          if (!$scope.$$destroyed) {
            app.serverGroups.onRefresh($scope, retrieveServerGroup);
          }
        })
        .catch(() => {});

      AccountService.getAccountDetails(serverGroup.accountId).then((details) => {
        const awsAccount = details.awsAccount;
        $scope.titusUiEndpoint = _.filter(details.regions, { name: serverGroup.region })[0].endpoint;
        AccountService.getAccountDetails(awsAccount).then((awsDetails) => {
          this.awsAccountId = awsDetails.accountId;
          this.env = awsDetails.environment;
        });
      });

      const configureEntityTagTargets = () => {
        this.entityTagTargets = ClusterTargetBuilder.buildClusterTargets($scope.serverGroup);
      };

      this.destroyServerGroup = function destroyServerGroup() {
        const serverGroup = $scope.serverGroup;

        const taskMonitor = {
          application: application,
          title: 'Destroying ' + serverGroup.name,
          onTaskComplete: function () {
            if ($state.includes('**.serverGroup', stateParams)) {
              $state.go('^');
            }
          },
        };

        const submitMethod = function () {
          return serverGroupWriter.destroyServerGroup(serverGroup, application, {
            cloudProvider: 'titus',
            serverGroupName: serverGroup.name,
            region: serverGroup.region,
          });
        };

        const stateParams = {
          name: serverGroup.name,
          accountId: serverGroup.account,
          region: serverGroup.region,
        };

        const confirmationModalParams = {
          header: 'Really destroy ' + serverGroup.name + '?',
          buttonText: 'Destroy ' + serverGroup.name,
          account: serverGroup.account,
          taskMonitorConfig: taskMonitor,
          platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
          platformHealthType: 'Titus',
          submitMethod: submitMethod,
        };

        ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);

        confirmNotManaged(serverGroup, app).then(
          (notManaged) => notManaged && ConfirmationModalService.confirm(confirmationModalParams),
        );
      };

      this.disableServerGroup = function disableServerGroup() {
        const serverGroup = $scope.serverGroup;

        const taskMonitor = {
          application: application,
          title: 'Disabling ' + serverGroup.name,
        };

        const submitMethod = function () {
          return serverGroupWriter.disableServerGroup(serverGroup, application, {
            cloudProvider: 'titus',
            serverGroupName: serverGroup.name,
            region: serverGroup.region,
            zone: serverGroup.zones[0],
          });
        };

        const confirmationModalParams = {
          header: 'Really disable ' + serverGroup.name + '?',
          buttonText: 'Disable ' + serverGroup.name,
          account: serverGroup.account,
          taskMonitorConfig: taskMonitor,
          platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
          platformHealthType: 'Titus',
          submitMethod: submitMethod,
        };

        ServerGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, confirmationModalParams);

        confirmNotManaged(serverGroup, app).then(
          (notManaged) => notManaged && ConfirmationModalService.confirm(confirmationModalParams),
        );
      };

      this.enableServerGroup = () => {
        confirmNotManaged(serverGroup, app).then((notManaged) => {
          if (!notManaged) {
            return;
          }
          if (!this.isRollbackEnabled()) {
            this.showEnableServerGroupModal();
            return;
          }

          const confirmationModalParams = {
            header: 'Rolling back?',
            body: `Spinnaker provides an orchestrated rollback feature to carefully restore a different version of this
                 server group. Do you want to use the orchestrated rollback?`,
            buttonText: `Yes, take me to the rollback settings modal`,
            cancelButtonText: 'No, I just want to enable the server group',
          };

          ConfirmationModalService.confirm(confirmationModalParams)
            // Wait for the confirmation modal to go away first to avoid react/angular bootstrap fighting
            // over the body.modal-open class
            .then(() => new Promise((resolve) => setTimeout(resolve, 500)))
            .then(() => this.rollbackServerGroup())
            .catch(({ source }) => {
              // don't show the enable modal if the user cancels with the header button
              if (source === 'footer') {
                this.showEnableServerGroupModal();
              }
            });
        });
      };

      this.resizeServerGroup = () => {
        confirmNotManaged(serverGroup, app).then((notManaged) => {
          notManaged && ReactModal.show(TitusResizeServerGroupModal, { serverGroup: $scope.serverGroup, application });
        });
      };

      this.showEnableServerGroupModal = () => {
        const serverGroup = $scope.serverGroup;

        const taskMonitor = {
          application: application,
          title: 'Enabling ' + serverGroup.name,
        };

        const submitMethod = function () {
          return serverGroupWriter.enableServerGroup(serverGroup, application, {
            cloudProvider: 'titus',
            serverGroupName: serverGroup.name,
            region: serverGroup.region,
            zone: serverGroup.zones[0],
          });
        };

        const confirmationModalParams = {
          header: 'Really enable ' + serverGroup.name + '?',
          buttonText: 'Enable ' + serverGroup.name,
          account: serverGroup.account,
          taskMonitorConfig: taskMonitor,
          platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
          platformHealthType: 'Titus',
          submitMethod: submitMethod,
        };

        ConfirmationModalService.confirm(confirmationModalParams);
      };

      this.cloneServerGroup = function cloneServerGroup() {
        TitusReactInjector.titusServerGroupCommandBuilder
          .buildServerGroupCommandFromExisting(application, $scope.serverGroup)
          .then((command) => {
            const title = `Clone ${serverGroup.name}`;
            TitusCloneServerGroupModal.show({ title, application, command });
          });
      };

      this.isRollbackEnabled = function rollbackServerGroup() {
        const serverGroup = $scope.serverGroup;
        if (!serverGroup.isDisabled) {
          // enabled server groups are always a candidate for rollback
          return true;
        }

        // if the server group selected for rollback is disabled, ensure that at least one enabled server group exists
        return application
          .getDataSource('serverGroups')
          .data.some(
            (g) =>
              g.cluster === serverGroup.cluster &&
              g.region === serverGroup.region &&
              g.account === serverGroup.account &&
              g.isDisabled === false,
          );
      };

      this.rollbackServerGroup = function rollbackServerGroup() {
        let serverGroup = $scope.serverGroup;

        let previousServerGroup;
        let allServerGroups = app
          .getDataSource('serverGroups')
          .data.filter(
            (g) =>
              g.cluster === serverGroup.cluster && g.region === serverGroup.region && g.account === serverGroup.account,
          );

        if (serverGroup.isDisabled) {
          // if the selected server group is disabled, it represents the server group that should be _rolled back to_
          previousServerGroup = serverGroup;

          /*
           * Find an existing server group to rollback, prefer the largest enabled server group.
           *
           * isRollbackEnabled() ensures that at least one enabled server group exists.
           */
          serverGroup = _.orderBy(
            allServerGroups.filter((g) => g.name !== previousServerGroup.name && !g.isDisabled),
            ['instanceCounts.total', 'createdTime'],
            ['desc', 'desc'],
          )[0];
        }

        // the set of all server groups should not include the server group selected for rollback
        allServerGroups = allServerGroups.filter((g) => g.name !== serverGroup.name);

        if (allServerGroups.length === 1 && !previousServerGroup) {
          // if there is only one other server group, default to it being the rollback target
          previousServerGroup = allServerGroups[0];
        }

        confirmNotManaged(serverGroup, app).then((notManaged) => {
          notManaged &&
            $uibModal.open({
              templateUrl: require('./rollback/rollbackServerGroup.html'),
              controller: 'titusRollbackServerGroupCtrl as ctrl',
              resolve: {
                serverGroup: () => serverGroup,
                previousServerGroup: () => previousServerGroup,
                disabledServerGroups: () => {
                  const cluster = _.find(application.clusters, {
                    name: serverGroup.cluster,
                    account: serverGroup.account,
                  });
                  return _.filter(cluster.serverGroups, { isDisabled: true, region: serverGroup.region });
                },
                allServerGroups: () => allServerGroups,
                application: () => application,
              },
            });
        });
      };
    },
  ]);
