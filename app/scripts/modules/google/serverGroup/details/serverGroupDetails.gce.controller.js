'use strict';
/* jshint camelcase:false */

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.gce.controller', [
  require('angular-ui-router'),
  require('../configure/ServerGroupCommandBuilder.js'),
  require('../../../core/application/modal/platformHealthOverride.directive.js'),
  require('../../../core/serverGroup/serverGroup.read.service.js'),
  require('../../../core/serverGroup/details/serverGroupWarningMessage.service.js'),
  require('../../../core/confirmationModal/confirmationModal.service.js'),
  require('../../../core/serverGroup/serverGroup.write.service.js'),
  require('../../../core/serverGroup/configure/common/runningExecutions.service.js'),
  require('../../../core/utils/lodash.js'),
  require('../../../core/insight/insightFilterState.model.js'),
  require('./resize/resizeServerGroup.controller'),
  require('./rollback/rollbackServerGroup.controller'),
  require('../../../core/utils/selectOnDblClick.directive.js'),
])
  .controller('gceServerGroupDetailsCtrl', function ($scope, $state, $templateCache, $interpolate, app, serverGroup, InsightFilterStateModel,
                                                     gceServerGroupCommandBuilder, serverGroupReader, $uibModal, confirmationModalService, _, serverGroupWriter,
                                                     runningExecutionsService, serverGroupWarningMessageService) {

    let application = app;

    $scope.state = {
      loading: true
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

    function extractServerGroupSummary() {
      var summary = _.find(application.serverGroups, function (toCheck) {
        return toCheck.name === serverGroup.name && toCheck.account === serverGroup.accountId && toCheck.region === serverGroup.region;
      });
      if (!summary) {
        application.loadBalancers.some(function (loadBalancer) {
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
      return serverGroupReader.getServerGroup(application.name, serverGroup.accountId, serverGroup.region, serverGroup.name).then(function(details) {
        cancelLoader();

        var restangularlessDetails = details.plain();
        angular.extend(restangularlessDetails, summary);

        $scope.serverGroup = restangularlessDetails;
        $scope.runningExecutions = function() {
          return runningExecutionsService.filterRunningExecutions($scope.serverGroup.executions);
        };

        if (!_.isEmpty($scope.serverGroup)) {
          if (details.securityGroups) {
            $scope.securityGroups = _(details.securityGroups).map(function(id) {
              return _.find(application.securityGroups, { 'accountName': serverGroup.accountId, 'region': 'global', 'id': id }) ||
                _.find(application.securityGroups, { 'accountName': serverGroup.accountId, 'region': 'global', 'name': id });
            }).compact().value();
          }

          var pathSegments = $scope.serverGroup.launchConfig.instanceTemplate.selfLink.split('/');
          var projectId = pathSegments[pathSegments.indexOf('projects') + 1];
          $scope.serverGroup.logsLink =
            'https://console.developers.google.com/project/' + projectId + '/logs?service=compute.googleapis.com&minLogLevel=0&filters=text:' + $scope.serverGroup.name;

          findStartupScript();
          prepareDiskDescriptions();
          prepareAvailabilityPolicies();
          prepareAuthScopes();
          augmentTagsWithHelp();
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

    function findStartupScript() {
      if (_.has($scope.serverGroup, 'launchConfig.instanceTemplate.properties.metadata.items')) {
        let metadataItems = $scope.serverGroup.launchConfig.instanceTemplate.properties.metadata.items;
        let startupScriptItem = _.find(metadataItems, metadataItem => {
          return metadataItem.key === 'startup-script';
        });

        if (startupScriptItem) {
          $scope.serverGroup.startupScript = startupScriptItem.value;
        }
      }
    }

    function prepareDiskDescriptions() {
      if (_.has($scope.serverGroup, 'launchConfig.instanceTemplate.properties.disks')) {
        let diskDescriptions = [];

        $scope.serverGroup.launchConfig.instanceTemplate.properties.disks.forEach(disk => {
          let diskLabel = disk.initializeParams.diskType + ":" + disk.initializeParams.diskSizeGb;
          let existingDiskDescription = _.find(diskDescriptions, description => {
            return description.bareLabel === diskLabel;
          });

          if (existingDiskDescription) {
            existingDiskDescription.count++;
            existingDiskDescription.countSuffix = ' (×' + existingDiskDescription.count + ')';
          } else {
            diskDescriptions.push({
              bareLabel: diskLabel,
              count: 1,
              countSuffix: '',
              finalLabel: translateDiskType(disk.initializeParams.diskType) + ": " + disk.initializeParams.diskSizeGb + "GB",
            });
          }
        });

        $scope.serverGroup.diskDescriptions = diskDescriptions;
      }
    }

    function prepareAvailabilityPolicies() {
      if (_.has($scope.serverGroup, 'launchConfig.instanceTemplate.properties.scheduling')) {
        let scheduling = $scope.serverGroup.launchConfig.instanceTemplate.properties.scheduling;

        $scope.serverGroup.availabilityPolicies = {
          preemptibility: scheduling.preemptible ? "On" : "Off",
          automaticRestart: scheduling.automaticRestart ? "On" : "Off",
          onHostMaintenance: scheduling.onHostMaintenance === "MIGRATE" ? "Migrate" : "Terminate",
        };
      }
    }

    function prepareAuthScopes() {
      if (_.has($scope.serverGroup, 'launchConfig.instanceTemplate.properties.serviceAccounts')) {
        let serviceAccounts = $scope.serverGroup.launchConfig.instanceTemplate.properties.serviceAccounts;
        let defaultServiceAccount = _.find(serviceAccounts, serviceAccount => {
          return serviceAccount.email === 'default';
        });

        if (defaultServiceAccount) {
          $scope.serverGroup.authScopes = _.map(defaultServiceAccount.scopes, authScope => {
            return authScope.replace('https://www.googleapis.com/auth/', '');
          });
        }
      }
    }

    function translateDiskType(diskType) {
      if (diskType === 'pd-ssd') {
        return 'Persistent SSD';
      } else if (diskType === 'local-ssd') {
        return 'Local SSD';
      } else {
        return 'Persistent Std';
      }
    }

    function augmentTagsWithHelp() {
      if (_.has($scope.serverGroup, 'launchConfig.instanceTemplate.properties.tags.items') && $scope.securityGroups) {
        let helpMap = {};

        $scope.serverGroup.launchConfig.instanceTemplate.properties.tags.items.forEach(tag => {
          let securityGroupsMatches = _.filter($scope.securityGroups, securityGroup => _.includes(securityGroup.targetTags, tag));
          let securityGroupMatchNames = _.pluck(securityGroupsMatches, 'name');

          if (!_.isEmpty(securityGroupMatchNames)) {
            let groupOrGroups = securityGroupMatchNames.length > 1 ? 'groups' : 'group';

            helpMap[tag] = 'This tag associates this server group with security ' + groupOrGroups + ' <em>' + securityGroupMatchNames.join(', ') + '</em>.';
          }
        });

        $scope.serverGroup.launchConfig.instanceTemplate.properties.tags.helpMap = helpMap;
      }
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
        application: application,
        title: 'Destroying ' + serverGroup.name,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true,
        katoPhaseToMonitor: 'DESTROY_ASG'
      };

      var submitMethod = function () {
        return serverGroupWriter.destroyServerGroup(serverGroup, application, {
          cloudProvider: 'gce',
          serverGroupName: serverGroup.name,
          region: serverGroup.region,
          zone: serverGroup.zones[0],
        });
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
        provider: 'gce',
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
        body: this.getBodyTemplate(serverGroup, application),
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

    this.getBodyTemplate = (serverGroup, application) => {
      if (this.isLastServerGroupInRegion(serverGroup, application)){
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

      var submitMethod = function(interestingHealthProviderNames) {
        return serverGroupWriter.disableServerGroup(serverGroup, application, {
          cloudProvider: 'gce',
          serverGroupName: serverGroup.name,
          region: serverGroup.region,
          zone: serverGroup.zones[0],
          interestingHealthProviderNames: interestingHealthProviderNames,
        });
      };

      var confirmationModalParams = {
        header: 'Really disable ' + serverGroup.name + '?',
        buttonText: 'Disable ' + serverGroup.name,
        destructive: true,
        provider: 'gce',
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        platformHealthOnlyShowOverride: application.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Google',
        submitMethod: submitMethod,
      };

      if (application.attributes.platformHealthOnly) {
        confirmationModalParams.interestingHealthProviderNames = ['Google'];
      }

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.enableServerGroup = function enableServerGroup() {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: application,
        title: 'Enabling ' + serverGroup.name,
        forceRefreshMessage: 'Refreshing application...',
      };

      var submitMethod = function(interestingHealthProviderNames) {
        return serverGroupWriter.enableServerGroup(serverGroup, application, {
          cloudProvider: 'gce',
          serverGroupName: serverGroup.name,
          region: serverGroup.region,
          zone: serverGroup.zones[0],
          interestingHealthProviderNames: interestingHealthProviderNames,
        });
      };

      var confirmationModalParams = {
        header: 'Really enable ' + serverGroup.name + '?',
        buttonText: 'Enable ' + serverGroup.name,
        destructive: false,
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        platformHealthOnlyShowOverride: application.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Google',
        submitMethod: submitMethod,
      };

      if (application.attributes.platformHealthOnly) {
        confirmationModalParams.interestingHealthProviderNames = ['Google'];
      }

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.rollbackServerGroup = function rollbackServerGroup() {
      $uibModal.open({
        templateUrl: require('./rollback/rollbackServerGroup.html'),
        controller: 'gceRollbackServerGroupCtrl as ctrl',
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

    this.resizeServerGroup = function resizeServerGroup() {
      $uibModal.open({
        templateUrl: require('./resize/resizeServerGroup.html'),
        controller: 'gceResizeServerGroupCtrl as ctrl',
        resolve: {
          serverGroup: function() { return $scope.serverGroup; },
          application: function() { return application; }
        }
      });
    };

    this.cloneServerGroup = function cloneServerGroup(serverGroup) {
      $uibModal.open({
        templateUrl: require('../configure/wizard/serverGroupWizard.html'),
        controller: 'gceCloneServerGroupCtrl as ctrl',
        resolve: {
          title: function() { return 'Clone ' + serverGroup.name; },
          application: function() { return application; },
          serverGroup: function() { return serverGroup; },
          serverGroupCommand: function() { return gceServerGroupCommandBuilder.buildServerGroupCommandFromExisting(application, serverGroup); },
        }
      });
    };

    this.showStartupScript = function showScalingActivities() {
      $scope.userDataModalTitle = "Startup Script";
      $scope.userData = $scope.serverGroup.startupScript;
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

    this.truncateCommitHash = function() {
      if ($scope.serverGroup && $scope.serverGroup.buildInfo && $scope.serverGroup.buildInfo.commit) {
        return $scope.serverGroup.buildInfo.commit.substring(0, 8);
      }
      return null;
    };

    this.getNetwork = function() {
      if ($scope.serverGroup &&
          $scope.serverGroup.launchConfig &&
          $scope.serverGroup.launchConfig.instanceTemplate &&
          $scope.serverGroup.launchConfig.instanceTemplate.properties &&
          $scope.serverGroup.launchConfig.instanceTemplate.properties.networkInterfaces) {
        var networkInterfaces = $scope.serverGroup.launchConfig.instanceTemplate.properties.networkInterfaces;

        if (networkInterfaces.length === 1) {
          var networkUrl = networkInterfaces[0].network;

          return _.last(networkUrl.split('/'));
        }
      }
      return null;
    };
  }
);
