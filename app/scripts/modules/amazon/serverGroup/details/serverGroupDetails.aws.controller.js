'use strict';
/* jshint camelcase:false */

require('../configure/serverGroup.configure.aws.module.js');

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.aws.controller', [
  require('angular-ui-router'),
  require('../../../core/application/modal/platformHealthOverride.directive.js'),
  require('../../../core/confirmationModal/confirmationModal.service.js'),
  require('../../../core/serverGroup/serverGroup.write.service.js'),
  require('../../../core/serverGroup/details/serverGroupWarningMessage.service.js'),
  require('../../../core/overrideRegistry/override.registry.js'),
  require('../../../core/account/account.service.js'),
  require('../../../core/utils/lodash.js'),
  require('../../vpc/vpcTag.directive.js'),
  require('./scalingProcesses/autoScalingProcess.service.js'),
  require('../../../core/serverGroup/serverGroup.read.service.js'),
  require('../configure/serverGroupCommandBuilder.service.js'),
  require('../../../core/serverGroup/configure/common/runningExecutions.service.js'),
  require('../../../netflix/migrator/serverGroup/serverGroup.migrator.directive.js'), // TODO: make actions pluggable
  require('./scalingPolicy/scalingPolicySummary.component.js'),
  require('./scheduledAction/scheduledAction.directive.js'),
  require('../../../core/insight/insightFilterState.model.js'),
  require('./scalingActivities/scalingActivities.controller.js'),
  require('./resize/resizeServerGroup.controller'),
  require('./rollback/rollbackServerGroup.controller'),
  require('../../../core/utils/selectOnDblClick.directive.js'),
  require('../serverGroup.transformer.js'),
  require('./scalingPolicy/addScalingPolicyButton.component.js'),
  require('./securityGroup/editSecurityGroups.modal.controller'),
])
  .controller('awsServerGroupDetailsCtrl', function ($scope, $state, app, serverGroup, InsightFilterStateModel,
                                                     serverGroupReader, awsServerGroupCommandBuilder, $uibModal,
                                                     confirmationModalService, _, serverGroupWriter, subnetReader,
                                                     autoScalingProcessService, runningExecutionsService,
                                                     awsServerGroupTransformer, accountService,
                                                     serverGroupWarningMessageService, overrideRegistry) {

    this.state = {
      loading: true
    };

    this.InsightFilterStateModel = InsightFilterStateModel;
    this.application = app;

    let extractServerGroupSummary = () => {
      return app
        .ready()
        .then(() => {
          var summary = _.find(app.serverGroups.data, (toCheck) => {
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

              if (!_.isEmpty(this.serverGroup)) {

                this.image = details.image ? details.image : undefined;

                var vpc = this.serverGroup.asg ? this.serverGroup.asg.vpczoneIdentifier : '';

                if (vpc !== '') {
                  var subnetId = vpc.split(',')[0];
                  subnetReader.listSubnets().then((subnets) => {
                    var subnet = _(subnets).find({'id': subnetId});
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

                if (details.launchConfig && details.launchConfig.securityGroups) {
                  this.securityGroups = _(details.launchConfig.securityGroups).map((id) => {
                    return _.find(app.securityGroups.data, { 'accountName': serverGroup.accountId, 'region': serverGroup.region, 'id': id }) ||
                      _.find(app.securityGroups.data, { 'accountName': serverGroup.accountId, 'region': serverGroup.region, 'name': id });
                  }).compact().value();
                }

                this.autoScalingProcesses = autoScalingProcessService.normalizeScalingProcesses(this.serverGroup);
                this.disabledDate = autoScalingProcessService.getDisabledDate(this.serverGroup);
                awsServerGroupTransformer.normalizeServerGroupDetails(this.serverGroup);
                this.scalingPolicies = this.serverGroup.scalingPolicies;
                this.scalingPoliciesDisabled = this.scalingPolicies.length && this.autoScalingProcesses
                    .filter(p => !p.enabled)
                    .some(p => ['Launch','Terminate','AlarmNotification'].indexOf(p.name) > -1);
                this.scheduledActionsDisabled = this.serverGroup.scheduledActions.length && this.autoScalingProcesses
                    .filter(p => !p.enabled)
                    .some(p => ['Launch','Terminate','ScheduledAction'].indexOf(p.name) > -1);

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

    this.runningExecutions = () => {
      return runningExecutionsService.filterRunningExecutions(this.serverGroup.executions);
    };

    this.isEnableLocked = () => {
      if (this.serverGroup.isDisabled) {
        let resizeTasks = (this.serverGroup.runningTasks || [])
          .filter(task => _.get(task, 'execution.stages', []).some(
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
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true,
        katoPhaseToMonitor: 'DESTROY_ASG'
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
        body: this.getBodyTemplate(serverGroup, app),
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
      };

      confirmationModalService.confirm(confirmationModalParams);

    };

    this.getBodyTemplate = (serverGroup, app) => {
      if (this.isLastServerGroupInRegion(serverGroup, app)) {
        return serverGroupWarningMessageService.getMessage(serverGroup);
      }
    };

    this.isLastServerGroupInRegion = (serverGroup, app) => {
      try {
        var cluster = _.find(app.clusters, {name: serverGroup.cluster, account: serverGroup.account});
        return _.filter(cluster.serverGroups, {region: serverGroup.region}).length === 1;
      } catch (error) {
        return false;
      }
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

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.rollbackServerGroup = () => {
      $uibModal.open({
        templateUrl: overrideRegistry.getTemplate('aws.rollback.modal', require('./rollback/rollbackServerGroup.html')),
        controller: 'awsRollbackServerGroupCtrl as ctrl',
        resolve: {
          serverGroup: () => this.serverGroup,
          disabledServerGroups: () => {
            var cluster = _.find(app.clusters, {name: this.serverGroup.cluster, account: this.serverGroup.account});
            return _.filter(cluster.serverGroups, {isDisabled: true, region: this.serverGroup.region});
          },
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

    this.showScalingActivities = () => {
      $uibModal.open({
        templateUrl: require('./scalingActivities/scalingActivities.html'),
        controller: 'ScalingActivitiesCtrl as ctrl',
        resolve: {
          applicationName: () => app.name,
          account: () => this.serverGroup.account,
          clusterName: () => this.serverGroup.cluster,
          serverGroup: () => this.serverGroup
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
        templateUrl: require('../../../core/serverGroup/details/userData.html'),
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
