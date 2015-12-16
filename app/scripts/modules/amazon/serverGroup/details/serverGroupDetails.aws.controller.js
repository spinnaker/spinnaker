'use strict';
/* jshint camelcase:false */

require('../configure/serverGroup.configure.aws.module.js');

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.aws.controller', [
  require('../../../core/application/modal/platformHealthOverride.directive.js'),
  require('../../../core/confirmationModal/confirmationModal.service.js'),
  require('../../../core/serverGroup/serverGroup.write.service.js'),
  require('../../../core/serverGroup/details/serverGroupWarningMessage.service.js'),
  require('../../../core/overrideRegistry/override.registry.js'),
  require('../../../core/utils/lodash.js'),
  require('../../vpc/vpcTag.directive.js'),
  require('./scalingProcesses/autoScalingProcess.service.js'),
  require('../../../core/serverGroup/serverGroup.read.service.js'),
  require('../configure/serverGroupCommandBuilder.service.js'),
  require('../../../core/serverGroup/configure/common/runningExecutions.service.js'),
  require('../../../netflix/migrator/serverGroup/serverGroup.migrator.directive.js'), // TODO: make actions pluggable
  require('./scalingPolicy/scalingPolicy.directive.js'),
  require('./scheduledAction/scheduledAction.directive.js'),
  require('../../../core/insight/insightFilterState.model.js'),
  require('./scalingActivities/scalingActivities.controller.js'),
  require('./resize/resizeServerGroup.controller'),
  require('./rollback/rollbackServerGroup.controller'),
  require('../../../core/utils/selectOnDblClick.directive.js'),
])
  .controller('awsServerGroupDetailsCtrl', function ($scope, $state, $templateCache, $interpolate, app, serverGroup, InsightFilterStateModel,
                                                     serverGroupReader, awsServerGroupCommandBuilder, $uibModal, confirmationModalService, _, serverGroupWriter,
                                                     subnetReader, autoScalingProcessService, runningExecutionsService, serverGroupWarningMessageService,
                                                     overrideRegistry) {

    $scope.state = {
      loading: true
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

    function applyAutoScalingProcesses() {
      $scope.autoScalingProcesses = [];
      if (!$scope.serverGroup.asg || !$scope.serverGroup.asg.suspendedProcesses) {
        return;
      }
      var disabled = $scope.serverGroup.asg.suspendedProcesses;
      var allProcesses = autoScalingProcessService.listProcesses();
      allProcesses.forEach(function(process) {
        let disabledProcess = _.find(disabled, {processName: process.name});
        let scalingProcess = {
          name: process.name,
          enabled: !disabledProcess,
          description: process.description,
        };
        if (disabledProcess) {
          let suspensionDate = disabledProcess.suspensionReason.replace('User suspended at ', '');
          scalingProcess.suspensionDate = new Date(suspensionDate).getTime();
        }
        $scope.autoScalingProcesses.push(scalingProcess);
      });
    }

    function extractServerGroupSummary() {
      var summary = _.find(app.serverGroups, function (toCheck) {
        return toCheck.name === serverGroup.name && toCheck.account === serverGroup.accountId && toCheck.region === serverGroup.region;
      });
      if (!summary) {
        app.loadBalancers.some(function (loadBalancer) {
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

    function retrieveServerGroup() {
      var summary = extractServerGroupSummary();
      return serverGroupReader.getServerGroup(app.name, serverGroup.accountId, serverGroup.region, serverGroup.name).then(function(details) {
        cancelLoader();

        var restangularlessDetails = details.plain();
        angular.extend(restangularlessDetails, summary);

        $scope.serverGroup = restangularlessDetails;
        $scope.runningExecutions = function() {
          return runningExecutionsService.filterRunningExecutions($scope.serverGroup.executions);
        };

        if (!_.isEmpty($scope.serverGroup)) {

          $scope.image = details.image ? details.image : undefined;

          var vpc = $scope.serverGroup.asg ? $scope.serverGroup.asg.vpczoneIdentifier : '';

          if (vpc !== '') {
            var subnetId = vpc.split(',')[0];
            subnetReader.listSubnets().then(function(subnets) {
              var subnet = _(subnets).find({'id': subnetId});
              $scope.serverGroup.subnetType = subnet.purpose;
            });
          }

          if (details.image && details.image.description) {
            var tags = details.image.description.split(', ');
            tags.forEach(function(tag) {
              var keyVal = tag.split('=');
              if (keyVal.length === 2 && keyVal[0] === 'ancestor_name') {
                details.image.baseImage = keyVal[1];
              }
            });
          }

          if (details.launchConfig && details.launchConfig.securityGroups) {
            $scope.securityGroups = _(details.launchConfig.securityGroups).map(function(id) {
              return _.find(app.securityGroups, { 'accountName': serverGroup.accountId, 'region': serverGroup.region, 'id': id }) ||
                _.find(app.securityGroups, { 'accountName': serverGroup.accountId, 'region': serverGroup.region, 'name': id });
            }).compact().value();
          }

          applyAutoScalingProcesses();

        } else {
          autoClose();
        }
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
      // do not bother subscribing to the autoRefreshStream
      if (!$scope.$$destroyed) {
        let refreshWatcher = app.autoRefreshStream.subscribe(retrieveServerGroup);
        $scope.$on('$destroy', () => refreshWatcher.dispose());
      }
    });

    this.destroyServerGroup = function destroyServerGroup() {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: app,
        title: 'Destroying ' + serverGroup.name,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true,
        katoPhaseToMonitor: 'DESTROY_ASG'
      };

      var submitMethod = function () {
        return serverGroupWriter.destroyServerGroup(serverGroup, app);
      };

      var stateParams = {
        name: serverGroup.name,
        accountId: serverGroup.account,
        region: serverGroup.region
      };


      confirmationModalService.confirm({
        header: 'Really destroy ' + serverGroup.name + '?',
        buttonText: 'Destroy ' + serverGroup.name,
        destructive: true,
        account: serverGroup.account,
        provider: 'aws',
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
        body: this.getBodyTemplate(serverGroup, app),
        onTaskComplete: function() {
          if ($state.includes('**.serverGroup', stateParams)) {
            $state.go('^');
          }
        },
        onApplicationRefresh: function() {
          if ($state.includes('**.serverGroup', stateParams)) {
            $state.go('^');
          }
        }
      });
    };

    this.getBodyTemplate = (serverGroup, app) => {
      if (this.isLastServerGroupInRegion(serverGroup, app)){
        return serverGroupWarningMessageService.getMessage(serverGroup);
      }
    };

    this.isLastServerGroupInRegion = function (serverGroup, app ) {
      try {
        var cluster = _.find(app.clusters, {name: serverGroup.cluster, account:serverGroup.account});
        return _.filter(cluster.serverGroups, {region: serverGroup.region}).length === 1;
      } catch (error) {
        return false;
      }
    };

    this.disableServerGroup = function disableServerGroup() {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: app,
        title: 'Disabling ' + serverGroup.name
      };

      var submitMethod = function(interestingHealthProviderNames) {
        return serverGroupWriter.disableServerGroup(serverGroup, app, {
          interestingHealthProviderNames: interestingHealthProviderNames,
        });
      };

      var confirmationModalParams = {
        header: 'Really disable ' + serverGroup.name + '?',
        buttonText: 'Disable ' + serverGroup.name,
        destructive: true,
        account: serverGroup.account,
        provider: 'aws',
        taskMonitorConfig: taskMonitor,
        platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Amazon',
        submitMethod: submitMethod,
      };

      if (app.attributes.platformHealthOnly) {
        confirmationModalParams.interestingHealthProviderNames = ['Amazon'];
      }

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.enableServerGroup = function enableServerGroup() {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: app,
        title: 'Enabling ' + serverGroup.name,
      };

      var submitMethod = function(interestingHealthProviderNames) {
        return serverGroupWriter.enableServerGroup(serverGroup, app, {
          interestingHealthProviderNames: interestingHealthProviderNames,
        });
      };

      var confirmationModalParams = {
        header: 'Really enable ' + serverGroup.name + '?',
        buttonText: 'Enable ' + serverGroup.name,
        destructive: false,
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Amazon',
        submitMethod: submitMethod,
      };

      if (app.attributes.platformHealthOnly) {
        confirmationModalParams.interestingHealthProviderNames = ['Amazon'];
      }

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.rollbackServerGroup = function rollbackServerGroup() {
      $uibModal.open({
        templateUrl: overrideRegistry.getTemplate('aws.rollback.modal', require('./rollback/rollbackServerGroup.html')),
        controller: 'awsRollbackServerGroupCtrl as ctrl',
        resolve: {
          serverGroup: function() { return $scope.serverGroup; },
          disabledServerGroups: function() {
            var cluster = _.find(app.clusters, {name: $scope.serverGroup.cluster, account: $scope.serverGroup.account});
            return _.filter(cluster.serverGroups, {isDisabled: true, region: $scope.serverGroup.region});
          },
          application: function() { return app; }
        }
      });
    };

    this.toggleScalingProcesses = function toggleScalingProcesses() {
      $uibModal.open({
        templateUrl: require('./scalingProcesses/modifyScalingProcesses.html'),
        controller: 'ModifyScalingProcessesCtrl as ctrl',
        resolve: {
          serverGroup: function() { return $scope.serverGroup; },
          application: function() { return app; },
          processes: function() { return $scope.autoScalingProcesses; },
        }
      });
    };

    this.resizeServerGroup = function resizeServerGroup() {
      $uibModal.open({
        templateUrl: overrideRegistry.getTemplate('aws.resize.modal', require('./resize/resizeServerGroup.html')),
        controller: 'awsResizeServerGroupCtrl as ctrl',
        resolve: {
          serverGroup: function() { return $scope.serverGroup; },
          application: function() { return app; }
        }
      });
    };

    this.cloneServerGroup = function cloneServerGroup(serverGroup) {
      $uibModal.open({
        templateUrl: require('../configure/wizard/serverGroupWizard.html'),
        controller: 'awsCloneServerGroupCtrl as ctrl',
        resolve: {
          title: function() { return 'Clone ' + serverGroup.name; },
          application: function() { return app; },
          serverGroupCommand: function() { return awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting(app, serverGroup); },
        }
      });
    };

    this.showScalingActivities = function showScalingActivities() {
      $scope.activities = [];
      $uibModal.open({
        templateUrl: require('./scalingActivities/scalingActivities.html'),
        controller: 'ScalingActivitiesCtrl as ctrl',
        resolve: {
          applicationName: function() { return app.name; },
          account: function() { return $scope.serverGroup.account; },
          clusterName: function() { return $scope.serverGroup.cluster; },
          serverGroup: function() { return $scope.serverGroup; }
        }
      });
    };

    this.showUserData = function showScalingActivities() {
      $scope.userData = window.atob($scope.serverGroup.launchConfig.userData);
      $uibModal.open({
        templateUrl: require('../../../core/serverGroup/details/userData.html'),
        controller: 'CloseableModalCtrl',
        scope: $scope
      });
    };

    this.buildJenkinsLink = function() {
      if ($scope.serverGroup && $scope.serverGroup.buildInfo && $scope.serverGroup.buildInfo.jenkins) {
        var jenkins = $scope.serverGroup.buildInfo.jenkins;
        return jenkins.host + 'job/' + jenkins.name + '/' + jenkins.number;
      }
      return null;
    };

    this.editScheduledActions = function () {
      $uibModal.open({
        templateUrl: require('./scheduledAction/editScheduledActions.modal.html'),
        controller: 'EditScheduledActionsCtrl as ctrl',
        resolve: {
          application: function() { return app; },
          serverGroup: function() { return $scope.serverGroup; }
        }
      });
    };

    this.editAdvancedSettings = function () {
      $uibModal.open({
        templateUrl: require('./advancedSettings/editAsgAdvancedSettings.modal.html'),
        controller: 'EditAsgAdvancedSettingsCtrl as ctrl',
        resolve: {
          application: function() { return app; },
          serverGroup: function() { return $scope.serverGroup; }
        }
      });
    };

    this.truncateCommitHash = function() {
      if ($scope.serverGroup && $scope.serverGroup.buildInfo && $scope.serverGroup.buildInfo.commit) {
        return $scope.serverGroup.buildInfo.commit.substring(0, 8);
      }
      return null;
    };
  }
);
