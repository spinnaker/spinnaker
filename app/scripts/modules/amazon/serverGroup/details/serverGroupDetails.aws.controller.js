'use strict';

import {chain, filter, find, get, isEmpty} from 'lodash';
let angular = require('angular');

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {ADD_ENTITY_TAG_LINKS_COMPONENT} from 'core/entityTag/addEntityTagLinks.component';
import {CONFIRMATION_MODAL_SERVICE} from 'core/confirmationModal/confirmationModal.service';
import {OVERRIDE_REGISTRY} from 'core/overrideRegistry/override.registry';
import {VIEW_SCALING_ACTIVITIES_LINK} from 'core/serverGroup/details/scalingActivities/viewScalingActivitiesLink.component';
import {SERVER_GROUP_READER} from 'core/serverGroup/serverGroupReader.service';
import {SERVER_GROUP_WRITER} from 'core/serverGroup/serverGroupWriter.service';
import {SERVER_GROUP_WARNING_MESSAGE_SERVICE} from 'core/serverGroup/details/serverGroupWarningMessage.service';
import {RUNNING_TASKS_DETAILS_COMPONENT} from 'core/serverGroup/details/runningTasks.component';
import {CLUSTER_TARGET_BUILDER} from 'core/entityTag/clusterTargetBuilder.service';
import {ENTITY_SOURCE_COMPONENT} from 'core/entityTag/entitySource.component';

require('../configure/serverGroup.configure.aws.module.js');

module.exports = angular.module('spinnaker.serverGroup.details.aws.controller', [
  require('angular-ui-router').default,
  require('core/application/modal/platformHealthOverride.directive.js'),
  CONFIRMATION_MODAL_SERVICE,
  SERVER_GROUP_WRITER,
  SERVER_GROUP_WARNING_MESSAGE_SERVICE,
  OVERRIDE_REGISTRY,
  ACCOUNT_SERVICE,
  VIEW_SCALING_ACTIVITIES_LINK,
  ADD_ENTITY_TAG_LINKS_COMPONENT,
  CLUSTER_TARGET_BUILDER,
  ENTITY_SOURCE_COMPONENT,
  require('../../vpc/vpcTag.directive.js'),
  require('./scalingProcesses/autoScalingProcess.service.js'),
  SERVER_GROUP_READER,
  require('../configure/serverGroupCommandBuilder.service.js'),
  require('../../../netflix/migrator/serverGroup/serverGroup.migrator.directive.js'), // TODO: make actions pluggable
  require('./scalingPolicy/scalingPolicySummary.component.js'),
  require('./scheduledAction/scheduledAction.directive.js'),
  require('./resize/resizeServerGroup.controller'),
  require('./rollback/rollbackServerGroup.controller'),
  require('core/utils/selectOnDblClick.directive.js'),
  require('../serverGroup.transformer.js'),
  require('./scalingPolicy/addScalingPolicyButton.component.js'),
  require('./securityGroup/editSecurityGroups.modal.controller'),
  RUNNING_TASKS_DETAILS_COMPONENT,
])
  .controller('awsServerGroupDetailsCtrl', function ($scope, $state, app, serverGroup,
                                                     serverGroupReader, awsServerGroupCommandBuilder, $uibModal,
                                                     confirmationModalService, serverGroupWriter, subnetReader,
                                                     autoScalingProcessService, clusterTargetBuilder,
                                                     awsServerGroupTransformer, accountService,
                                                     serverGroupWarningMessageService, overrideRegistry) {

    this.state = {
      loading: true
    };

    this.application = app;

    let extractServerGroupSummary = () => {
      return app
        .ready()
        .then(() => {
          var summary = find(app.serverGroups.data, (toCheck) => {
            return toCheck.name === serverGroup.name && toCheck.account === serverGroup.accountId && toCheck.region === serverGroup.region;
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

    let autoClose = () => {
      if ($scope.$$destroyed) {
        return;
      }
      $state.params.allowModalToStayOpen = true;
      $state.go('^', null, {location: 'replace'});
    };

    let cancelLoader = () => {
      this.state.loading = false;
    };

    let retrieveServerGroup = () => {
      return extractServerGroupSummary()
        .then((summary) => {
          return serverGroupReader.getServerGroup(app.name, serverGroup.accountId, serverGroup.region, serverGroup.name)
            .then((details) => {
              cancelLoader();

              var plainDetails = details;
              angular.extend(plainDetails, summary);
              // it's possible the summary was not found because the clusters are still loading
              plainDetails.account = serverGroup.accountId;

              this.serverGroup = plainDetails;
              this.applyAccountDetails(this.serverGroup);

              if (!isEmpty(this.serverGroup)) {

                this.image = details.image ? details.image : undefined;
                this.enabledMetrics = get(this.serverGroup, 'asg.enabledMetrics', []).map(m => m.metric);

                var vpc = this.serverGroup.asg ? this.serverGroup.asg.vpczoneIdentifier : '';

                if (vpc !== '') {
                  var subnetId = vpc.split(',')[0];
                  subnetReader.listSubnets().then((subnets) => {
                    var subnet = chain(subnets).find({'id': subnetId}).value();
                    this.serverGroup.subnetType = subnet.purpose;
                  });
                }

                if (details.image && details.image.description) {
                  var tags = details.image.description.split(', ');
                  tags.forEach((tag) => {
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

                if (details.launchConfig && details.launchConfig.securityGroups) {
                  this.securityGroups = chain(details.launchConfig.securityGroups).map((id) => {
                    return find(app.securityGroups.data, { 'accountName': serverGroup.accountId, 'region': serverGroup.region, 'id': id }) ||
                      find(app.securityGroups.data, { 'accountName': serverGroup.accountId, 'region': serverGroup.region, 'name': id });
                  }).compact().value();
                }

                this.autoScalingProcesses = autoScalingProcessService.normalizeScalingProcesses(this.serverGroup);
                this.disabledDate = autoScalingProcessService.getDisabledDate(this.serverGroup);
                awsServerGroupTransformer.normalizeServerGroupDetails(this.serverGroup);
                this.scalingPolicies = this.serverGroup.scalingPolicies;
                this.scalingPoliciesDisabled = this.scalingPolicies.length && this.autoScalingProcesses
                    .filter(p => !p.enabled)
                    .some(p => ['Launch','Terminate','AlarmNotification'].includes(p.name));
                this.scheduledActionsDisabled = this.serverGroup.scheduledActions.length && this.autoScalingProcesses
                    .filter(p => !p.enabled)
                    .some(p => ['Launch','Terminate','ScheduledAction'].includes(p.name));
                configureEntityTagTargets();

                this.changeConfig = {
                  metadata: get(this.serverGroup.entityTags, 'creationMetadata')
                };
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

    let configureEntityTagTargets = () => {
      this.entityTagTargets = clusterTargetBuilder.buildClusterTargets(this.serverGroup);
    };

    this.isEnableLocked = () => {
      if (this.serverGroup.isDisabled) {
        let resizeTasks = (this.serverGroup.runningTasks || [])
          .filter(task => get(task, 'execution.stages', []).some(
            stage => stage.type === 'resizeServerGroup'));
        if (resizeTasks.length) {
          return true;
        }
      }
      return false;
    };

    this.destroyServerGroup = () => {
      var serverGroup = this.serverGroup;

      var taskMonitor = {
        application: app,
        title: 'Destroying ' + serverGroup.name,
      };

      var submitMethod = (params) => serverGroupWriter.destroyServerGroup(serverGroup, app, params);

      var stateParams = {
        name: serverGroup.name,
        accountId: serverGroup.account,
        region: serverGroup.region
      };

      var confirmationModalParams = {
        header: 'Really destroy ' + serverGroup.name + '?',
        buttonText: 'Destroy ' + serverGroup.name,
        account: serverGroup.account,
        provider: 'aws',
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
        askForReason: true,
        platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Amazon',
        onTaskComplete: () => {
          if ($state.includes('**.serverGroup', stateParams)) {
            $state.go('^');
          }
        }
      };

      serverGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);

      if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
        confirmationModalParams.interestingHealthProviderNames = ['Amazon'];
      }

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.disableServerGroup = () => {
      var serverGroup = this.serverGroup;

      var taskMonitor = {
        application: app,
        title: 'Disabling ' + serverGroup.name
      };

      var submitMethod = (params) => {
        return serverGroupWriter.disableServerGroup(serverGroup, app, params);
      };

      var confirmationModalParams = {
        header: 'Really disable ' + serverGroup.name + '?',
        buttonText: 'Disable ' + serverGroup.name,
        account: serverGroup.account,
        provider: 'aws',
        taskMonitorConfig: taskMonitor,
        platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Amazon',
        submitMethod: submitMethod,
        askForReason: true
      };

      serverGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, confirmationModalParams);

      if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
        confirmationModalParams.interestingHealthProviderNames = ['Amazon'];
      }

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.enableServerGroup = () => {
      var serverGroup = this.serverGroup;

      var taskMonitor = {
        application: app,
        title: 'Enabling ' + serverGroup.name,
      };

      var submitMethod = (params) => {
        return serverGroupWriter.enableServerGroup(serverGroup, app, params);
      };

      var confirmationModalParams = {
        header: 'Really enable ' + serverGroup.name + '?',
        buttonText: 'Enable ' + serverGroup.name,
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Amazon',
        submitMethod: submitMethod,
        askForReason: true
      };

      if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
        confirmationModalParams.interestingHealthProviderNames = ['Amazon'];
      }

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.rollbackServerGroup = () => {
      $uibModal.open({
        templateUrl: overrideRegistry.getTemplate('aws.rollback.modal', require('./rollback/rollbackServerGroup.html')),
        controller: 'awsRollbackServerGroupCtrl as ctrl',
        resolve: {
          serverGroup: () => this.serverGroup,
          disabledServerGroups: () => {
            var cluster = find(app.clusters, {name: this.serverGroup.cluster, account: this.serverGroup.account});
            return filter(cluster.serverGroups, {isDisabled: true, region: this.serverGroup.region});
          },
          allServerGroups: () => app.getDataSource('serverGroups').data.filter(g =>
            g.cluster === this.serverGroup.cluster &&
            g.region === this.serverGroup.region &&
            g.account === this.serverGroup.account &&
            g.name !== this.serverGroup.name
          ),
          application: () => app
        }
      });
    };

    this.toggleScalingProcesses = () => {
      $uibModal.open({
        templateUrl: require('./scalingProcesses/modifyScalingProcesses.html'),
        controller: 'ModifyScalingProcessesCtrl as ctrl',
        resolve: {
          serverGroup: () => this.serverGroup,
          application: () => app,
          processes: () => this.autoScalingProcesses,
        }
      });
    };

    this.resizeServerGroup = () => {
      $uibModal.open({
        templateUrl: overrideRegistry.getTemplate('aws.resize.modal', require('./resize/resizeServerGroup.html')),
        controller: 'awsResizeServerGroupCtrl as ctrl',
        resolve: {
          serverGroup: () => this.serverGroup,
          application: () => app
        }
      });
    };

    this.cloneServerGroup = (serverGroup) => {
      $uibModal.open({
        templateUrl: require('../configure/wizard/serverGroupWizard.html'),
        controller: 'awsCloneServerGroupCtrl as ctrl',
        size: 'lg',
        resolve: {
          title: () => 'Clone ' + serverGroup.name,
          application: () => app,
          serverGroupCommand: () => awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting(app, serverGroup),
        }
      });
    };

    this.updateSecurityGroups = () => {
      $uibModal.open({
        templateUrl: require('./securityGroup/editSecurityGroups.modal.html'),
        controller: 'EditSecurityGroupsCtrl as $ctrl',
        resolve: {
          application: () => app,
          serverGroup: () => this.serverGroup,
          securityGroups: () => this.securityGroups
        }
      });
    };

    this.showUserData = () => {
      // TODO: Provide a custom controller so we don't have to stick this on the scope
      $scope.userData = window.atob(this.serverGroup.launchConfig.userData);
      $scope.serverGroup = { name: this.serverGroup.name };
      $uibModal.open({
        templateUrl: require('core/serverGroup/details/userData.html'),
        controller: 'CloseableModalCtrl',
        scope: $scope
      });
    };

    this.buildJenkinsLink = () => {
      if (this.serverGroup && this.serverGroup.buildInfo && this.serverGroup.buildInfo.buildInfoUrl) {
        return this.serverGroup.buildInfo.buildInfoUrl;
      } else if (this.serverGroup && this.serverGroup.buildInfo && this.serverGroup.buildInfo.jenkins) {
        var jenkins = this.serverGroup.buildInfo.jenkins;
        return jenkins.host + 'job/' + jenkins.name + '/' + jenkins.number;
      }
      return null;
    };

    this.editScheduledActions = () => {
      $uibModal.open({
        templateUrl: require('./scheduledAction/editScheduledActions.modal.html'),
        controller: 'EditScheduledActionsCtrl as ctrl',
        resolve: {
          application: () => app,
          serverGroup: () => this.serverGroup
        }
      });
    };

    this.editAdvancedSettings = () => {
      $uibModal.open({
        templateUrl: require('./advancedSettings/editAsgAdvancedSettings.modal.html'),
        controller: 'EditAsgAdvancedSettingsCtrl as ctrl',
        resolve: {
          application: () => app,
          serverGroup: () => this.serverGroup
        }
      });
    };

    this.truncateCommitHash = () => {
      if (this.serverGroup && this.serverGroup.buildInfo && this.serverGroup.buildInfo.commit) {
        return this.serverGroup.buildInfo.commit.substring(0, 8);
      }
      return null;
    };

    this.applyAccountDetails = (serverGroup) => {
      return accountService.getAccountDetails(serverGroup.account).then((details) => {
        serverGroup.accountDetails = details;
      });
    };
  }
);
