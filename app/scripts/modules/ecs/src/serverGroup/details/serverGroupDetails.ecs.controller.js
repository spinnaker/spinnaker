'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';
import { chain, filter, find, has, isEmpty } from 'lodash';

import {
  AccountService,
  ConfirmationModalService,
  FirewallLabels,
  OVERRIDE_REGISTRY,
  SERVER_GROUP_WRITER,
  ServerGroupReader,
  ServerGroupWarningMessageService,
  SubnetReader,
} from '@spinnaker/core';

import { ECS_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE } from '../configure/serverGroupCommandBuilder.service';
import { ECS_SERVERGROUP_DETAILS_RESIZE_RESIZESERVERGROUP_CONTROLLER } from './resize/resizeServerGroup.controller';
import { ECS_SERVERGROUP_DETAILS_ROLLBACK_ROLLBACKSERVERGROUP_CONTROLLER } from './rollback/rollbackServerGroup.controller';
import { ECS_SERVER_GROUP_TRANSFORMER } from '../serverGroup.transformer';

export const ECS_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_ECS_CONTROLLER = 'spinnaker.ecs.serverGroup.details.controller';
export const name = ECS_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_ECS_CONTROLLER; // for backwards compatibility
angular
  .module(ECS_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_ECS_CONTROLLER, [
    UIROUTER_ANGULARJS,
    ECS_SERVER_GROUP_TRANSFORMER,
    OVERRIDE_REGISTRY,
    SERVER_GROUP_WRITER,
    ECS_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE,
    ECS_SERVERGROUP_DETAILS_RESIZE_RESIZESERVERGROUP_CONTROLLER,
    ECS_SERVERGROUP_DETAILS_ROLLBACK_ROLLBACKSERVERGROUP_CONTROLLER,
  ])
  .controller('ecsServerGroupDetailsCtrl', [
    '$scope',
    '$state',
    'app',
    'serverGroup',
    'ecsServerGroupCommandBuilder',
    '$uibModal',
    'serverGroupWriter',
    'ecsServerGroupTransformer',
    'overrideRegistry',
    function (
      $scope,
      $state,
      app,
      serverGroup,
      ecsServerGroupCommandBuilder,
      $uibModal,
      serverGroupWriter,
      ecsServerGroupTransformer,
      overrideRegistry,
    ) {
      this.state = {
        loading: true,
      };

      this.firewallsLabel = FirewallLabels.get('Firewalls');

      this.application = app;

      const extractServerGroupSummary = () => {
        return app.ready().then(() => {
          let summary = find(app.serverGroups.data, (toCheck) => {
            return (
              toCheck.name === serverGroup.name &&
              toCheck.account === serverGroup.accountId &&
              toCheck.region === serverGroup.region
            );
          });
          if (!summary) {
            app.loadBalancers.data.some((loadBalancer) => {
              if (loadBalancer.account === serverGroup.accountId && loadBalancer.region === serverGroup.region) {
                return loadBalancer.serverGroups.some((possibleServerGroup) => {
                  if (possibleServerGroup.name === serverGroup.name) {
                    summary = possibleServerGroup;
                    return true;
                  }
                });
              }
            });
          }
          return summary;
        });
      };

      const autoClose = () => {
        if ($scope.$$destroyed) {
          return;
        }
        $state.go('^', { allowModalToStayOpen: true }, { location: 'replace' });
      };

      const cancelLoader = () => {
        this.state.loading = false;
      };

      const retrieveServerGroup = () => {
        return extractServerGroupSummary()
          .then((summary) => {
            return ServerGroupReader.getServerGroup(
              app.name,
              serverGroup.accountId,
              serverGroup.region,
              serverGroup.name,
            ).then((details) => {
              cancelLoader();

              // it's possible the summary was not found because the clusters are still loading
              angular.extend(details, summary, { account: serverGroup.accountId });

              this.serverGroup = ecsServerGroupTransformer.normalizeServerGroupDetails(details);
              this.applyAccountDetails(this.serverGroup);

              if (!isEmpty(this.serverGroup)) {
                this.image = details.image ? details.image : undefined;

                const vpc = this.serverGroup.asg ? this.serverGroup.asg.vpczoneIdentifier : '';

                if (vpc !== '') {
                  const subnetId = vpc.split(',')[0];
                  SubnetReader.listSubnets().then((subnets) => {
                    const subnet = chain(subnets).find({ id: subnetId }).value();
                    this.serverGroup.subnetType = subnet.purpose;
                  });
                }

                if (details.image && details.image.description) {
                  const tags = details.image.description.split(', ');
                  tags.forEach((tag) => {
                    const keyVal = tag.split('=');
                    if (keyVal.length === 2 && keyVal[0] === 'ancestor_name') {
                      details.image.baseImage = keyVal[1];
                    }
                  });
                }

                if (details.image && details.image.tags) {
                  const baseAmiVersionTag = details.image.tags.find((tag) => tag.key === 'base_ami_version');
                  if (baseAmiVersionTag) {
                    details.baseAmiVersion = baseAmiVersionTag.value;
                  }
                }

                this.scalingPolicies = this.serverGroup.scalingPolicies;
                // TODO - figure out whether we need the commented out block below, or not.  IF we do, then we need to make it stop crashing

                if (has(this.serverGroup, 'buildInfo.jenkins')) {
                  this.changeConfig.buildInfo = {
                    jenkins: this.serverGroup.buildInfo.jenkins,
                  };
                }

                /*this.scalingPoliciesDisabled = this.scalingPolicies.length && this.autoScalingProcesses
                    .filter(p => !p.enabled)
                    .some(p => ['Launch','Terminate','AlarmNotification'].includes(p.name));
                this.scheduledActionsDisabled = this.serverGroup.scheduledActions.length && this.autoScalingProcesses
                    .filter(p => !p.enabled)
                    .some(p => ['Launch','Terminate','ScheduledAction'].includes(p.name));
                configureEntityTagTargets();

                this.changeConfig = {
                  metadata: get(this.serverGroup.entityTags, 'creationMetadata')
                };
                */
              } else {
                autoClose();
              }
            });
          })
          .catch(autoClose);
      };

      retrieveServerGroup().then(() => {
        // If the user navigates away from the view before the initial retrieveServerGroup call completes,
        // do not bother subscribing to the refresh
        if (!$scope.$$destroyed) {
          app.serverGroups.onRefresh($scope, retrieveServerGroup);
        }
      });

      this.destroyServerGroup = () => {
        const serverGroup = this.serverGroup;

        const taskMonitor = {
          application: app,
          title: 'Destroying ' + serverGroup.name,
          onTaskComplete: () => {
            if ($state.includes('**.serverGroup', stateParams)) {
              $state.go('^');
            }
          },
        };

        const submitMethod = (params) => serverGroupWriter.destroyServerGroup(serverGroup, app, params);

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
          submitMethod: submitMethod,
          askForReason: true,
          platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
          platformHealthType: 'Ecs',
        };

        ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);

        if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
          confirmationModalParams.interestingHealthProviderNames = ['Ecs'];
        }

        ConfirmationModalService.confirm(confirmationModalParams);
      };

      this.disableServerGroup = () => {
        const serverGroup = this.serverGroup;

        const taskMonitor = {
          application: app,
          title: 'Disabling ' + serverGroup.name,
        };

        const submitMethod = (params) => {
          return serverGroupWriter.disableServerGroup(serverGroup, app, params);
        };

        const confirmationModalParams = {
          header: 'Really disable ' + serverGroup.name + '?',
          buttonText: 'Disable ' + serverGroup.name,
          account: serverGroup.account,
          taskMonitorConfig: taskMonitor,
          platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
          platformHealthType: 'Ecs',
          submitMethod: submitMethod,
          askForReason: true,
        };

        ServerGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, confirmationModalParams);

        if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
          confirmationModalParams.interestingHealthProviderNames = ['Ecs'];
        }

        ConfirmationModalService.confirm(confirmationModalParams);
      };

      this.enableServerGroup = () => {
        const serverGroup = this.serverGroup;

        const taskMonitor = {
          application: app,
          title: 'Enabling ' + serverGroup.name,
        };

        const submitMethod = (params) => {
          return serverGroupWriter.enableServerGroup(serverGroup, app, params);
        };

        const confirmationModalParams = {
          header: 'Really enable ' + serverGroup.name + '?',
          buttonText: 'Enable ' + serverGroup.name,
          account: serverGroup.account,
          taskMonitorConfig: taskMonitor,
          platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
          platformHealthType: 'Ecs',
          submitMethod: submitMethod,
          askForReason: true,
        };

        if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
          confirmationModalParams.interestingHealthProviderNames = ['Ecs'];
        }

        ConfirmationModalService.confirm(confirmationModalParams);
      };

      this.rollbackServerGroup = () => {
        $uibModal.open({
          templateUrl: overrideRegistry.getTemplate(
            'ecs.rollback.modal',
            require('./rollback/rollbackServerGroup.html'),
          ),
          windowClass: 'modal-z-index',
          controller: 'ecsRollbackServerGroupCtrl as ctrl',
          resolve: {
            serverGroup: () => this.serverGroup,
            disabledServerGroups: () => {
              const cluster = find(app.clusters, { name: this.serverGroup.cluster, account: this.serverGroup.account });
              return filter(cluster.serverGroups, { isDisabled: true, region: this.serverGroup.region });
            },
            allServerGroups: () =>
              app
                .getDataSource('serverGroups')
                .data.filter(
                  (g) =>
                    g.cluster === this.serverGroup.cluster &&
                    g.region === this.serverGroup.region &&
                    g.account === this.serverGroup.account &&
                    g.name !== this.serverGroup.name,
                ),
            application: () => app,
          },
        });
      };

      this.resizeServerGroup = () => {
        $uibModal.open({
          templateUrl: overrideRegistry.getTemplate('ecs.resize.modal', require('./resize/resizeServerGroup.html')),
          controller: 'ecsResizeServerGroupCtrl as ctrl',
          windowClass: 'modal-z-index',
          resolve: {
            serverGroup: () => this.serverGroup,
            application: () => app,
          },
        });
      };

      this.cloneServerGroup = (serverGroup) => {
        $uibModal.open({
          templateUrl: require('../configure/wizard/serverGroupWizard.html'),
          controller: 'ecsCloneServerGroupCtrl as ctrl',
          size: 'lg',
          windowClass: 'modal-z-index',
          resolve: {
            title: () => 'Clone ' + serverGroup.name,
            application: () => app,
            serverGroupCommand: () =>
              ecsServerGroupCommandBuilder.buildServerGroupCommandFromExisting(app, serverGroup),
          },
        });
      };

      this.buildJenkinsLink = () => {
        if (has(this, 'serverGroup.buildInfo.buildInfoUrl')) {
          return this.serverGroup.buildInfo.buildInfoUrl;
        } else if (has(this, 'serverGroup.buildInfo.jenkins')) {
          const jenkins = this.serverGroup.buildInfo.jenkins;
          return jenkins.host + 'job/' + jenkins.name + '/' + jenkins.number;
        }
        return null;
      };

      this.truncateCommitHash = () => {
        if (this.serverGroup && this.serverGroup.buildInfo && this.serverGroup.buildInfo.commit) {
          return this.serverGroup.buildInfo.commit.substring(0, 8);
        }
        return null;
      };

      this.applyAccountDetails = (serverGroup) => {
        return AccountService.getAccountDetails(serverGroup.account).then((details) => {
          serverGroup.accountDetails = details;
        });
      };
    },
  ]);
