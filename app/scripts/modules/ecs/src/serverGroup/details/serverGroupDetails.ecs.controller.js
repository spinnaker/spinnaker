'use strict';

const angular = require('angular');
import { chain, filter, find, has, isEmpty } from 'lodash';
import { FirewallLabels } from '@spinnaker/core';

import { ECS_SERVER_GROUP_TRANSFORMER } from '../serverGroup.transformer';

import {
  AccountService,
  CONFIRMATION_MODAL_SERVICE,
  OVERRIDE_REGISTRY,
  ServerGroupReader,
  SERVER_GROUP_WRITER,
  ServerGroupWarningMessageService,
  SubnetReader,
} from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.ecs.serverGroup.details.controller', [
    require('@uirouter/angularjs').default,
    ECS_SERVER_GROUP_TRANSFORMER,
    CONFIRMATION_MODAL_SERVICE,
    OVERRIDE_REGISTRY,
    SERVER_GROUP_WRITER,
    require('../configure/serverGroupCommandBuilder.service.js').name,
    require('./resize/resizeServerGroup.controller').name,
    require('./rollback/rollbackServerGroup.controller').name,
  ])
  .controller('ecsServerGroupDetailsCtrl', function(
    $scope,
    $state,
    app,
    serverGroup,
    ecsServerGroupCommandBuilder,
    $uibModal,
    confirmationModalService,
    serverGroupWriter,
    ecsServerGroupTransformer,
    overrideRegistry,
  ) {
    this.state = {
      loading: true,
    };

    this.firewallsLabel = FirewallLabels.get('Firewalls');

    this.application = app;

    let extractServerGroupSummary = () => {
      return app.ready().then(() => {
        var summary = find(app.serverGroups.data, toCheck => {
          return (
            toCheck.name === serverGroup.name &&
            toCheck.account === serverGroup.accountId &&
            toCheck.region === serverGroup.region
          );
        });
        if (!summary) {
          app.loadBalancers.data.some(loadBalancer => {
            if (loadBalancer.account === serverGroup.accountId && loadBalancer.region === serverGroup.region) {
              return loadBalancer.serverGroups.some(possibleServerGroup => {
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

    let autoClose = () => {
      if ($scope.$$destroyed) {
        return;
      }
      $state.go('^', { allowModalToStayOpen: true }, { location: 'replace' });
    };

    let cancelLoader = () => {
      this.state.loading = false;
    };

    let retrieveServerGroup = () => {
      return extractServerGroupSummary()
        .then(summary => {
          return ServerGroupReader.getServerGroup(
            app.name,
            serverGroup.accountId,
            serverGroup.region,
            serverGroup.name,
          ).then(details => {
            cancelLoader();

            // it's possible the summary was not found because the clusters are still loading
            angular.extend(details, summary, { account: serverGroup.accountId });

            this.serverGroup = ecsServerGroupTransformer.normalizeServerGroupDetails(details);
            this.applyAccountDetails(this.serverGroup);

            if (!isEmpty(this.serverGroup)) {
              this.image = details.image ? details.image : undefined;

              var vpc = this.serverGroup.asg ? this.serverGroup.asg.vpczoneIdentifier : '';

              if (vpc !== '') {
                var subnetId = vpc.split(',')[0];
                SubnetReader.listSubnets().then(subnets => {
                  var subnet = chain(subnets)
                    .find({ id: subnetId })
                    .value();
                  this.serverGroup.subnetType = subnet.purpose;
                });
              }

              if (details.image && details.image.description) {
                var tags = details.image.description.split(', ');
                tags.forEach(tag => {
                  var keyVal = tag.split('=');
                  if (keyVal.length === 2 && keyVal[0] === 'ancestor_name') {
                    details.image.baseImage = keyVal[1];
                  }
                });
              }

              if (details.image && details.image.tags) {
                var baseAmiVersionTag = details.image.tags.find(tag => tag.key === 'base_ami_version');
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
      var serverGroup = this.serverGroup;

      var taskMonitor = {
        application: app,
        title: 'Destroying ' + serverGroup.name,
      };

      var submitMethod = params => serverGroupWriter.destroyServerGroup(serverGroup, app, params);

      var stateParams = {
        name: serverGroup.name,
        accountId: serverGroup.account,
        region: serverGroup.region,
      };

      var confirmationModalParams = {
        header: 'Really destroy ' + serverGroup.name + '?',
        buttonText: 'Destroy ' + serverGroup.name,
        account: serverGroup.account,
        provider: 'ecs',
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
        askForReason: true,
        platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Ecs',
        onTaskComplete: () => {
          if ($state.includes('**.serverGroup', stateParams)) {
            $state.go('^');
          }
        },
      };

      ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);

      if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
        confirmationModalParams.interestingHealthProviderNames = ['Ecs'];
      }

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.disableServerGroup = () => {
      var serverGroup = this.serverGroup;

      var taskMonitor = {
        application: app,
        title: 'Disabling ' + serverGroup.name,
      };

      var submitMethod = params => {
        return serverGroupWriter.disableServerGroup(serverGroup, app, params);
      };

      var confirmationModalParams = {
        header: 'Really disable ' + serverGroup.name + '?',
        buttonText: 'Disable ' + serverGroup.name,
        account: serverGroup.account,
        provider: 'ecs',
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

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.enableServerGroup = () => {
      var serverGroup = this.serverGroup;

      var taskMonitor = {
        application: app,
        title: 'Enabling ' + serverGroup.name,
      };

      var submitMethod = params => {
        return serverGroupWriter.enableServerGroup(serverGroup, app, params);
      };

      var confirmationModalParams = {
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

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.rollbackServerGroup = () => {
      $uibModal.open({
        templateUrl: overrideRegistry.getTemplate('ecs.rollback.modal', require('./rollback/rollbackServerGroup.html')),
        controller: 'ecsRollbackServerGroupCtrl as ctrl',
        resolve: {
          serverGroup: () => this.serverGroup,
          disabledServerGroups: () => {
            var cluster = find(app.clusters, { name: this.serverGroup.cluster, account: this.serverGroup.account });
            return filter(cluster.serverGroups, { isDisabled: true, region: this.serverGroup.region });
          },
          allServerGroups: () =>
            app
              .getDataSource('serverGroups')
              .data.filter(
                g =>
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
        resolve: {
          serverGroup: () => this.serverGroup,
          application: () => app,
        },
      });
    };

    this.cloneServerGroup = serverGroup => {
      $uibModal.open({
        templateUrl: require('../configure/wizard/serverGroupWizard.html'),
        controller: 'ecsCloneServerGroupCtrl as ctrl',
        size: 'lg',
        resolve: {
          title: () => 'Clone ' + serverGroup.name,
          application: () => app,
          serverGroupCommand: () => ecsServerGroupCommandBuilder.buildServerGroupCommandFromExisting(app, serverGroup),
        },
      });
    };

    this.buildJenkinsLink = () => {
      if (has(this, 'serverGroup.buildInfo.buildInfoUrl')) {
        return this.serverGroup.buildInfo.buildInfoUrl;
      } else if (has(this, 'serverGroup.buildInfo.jenkins')) {
        var jenkins = this.serverGroup.buildInfo.jenkins;
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

    this.applyAccountDetails = serverGroup => {
      return AccountService.getAccountDetails(serverGroup.account).then(details => {
        serverGroup.accountDetails = details;
      });
    };
  });
