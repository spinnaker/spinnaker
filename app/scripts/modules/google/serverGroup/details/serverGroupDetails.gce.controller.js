'use strict';

import _ from 'lodash';

import {CONFIRMATION_MODAL_SERVICE} from 'core/confirmationModal/confirmationModal.service';
import {NETWORK_READ_SERVICE} from 'core/network/network.read.service';
import {SERVER_GROUP_READER_SERVICE} from 'core/serverGroup/serverGroupReader.service';
import {SERVER_GROUP_WRITER_SERVICE} from 'core/serverGroup/serverGroupWriter.service';
import {SERVER_GROUP_WARNING_MESSAGE_SERVICE} from 'core/serverGroup/details/serverGroupWarningMessage.service';

let angular = require('angular');

require('../configure/serverGroup.configure.gce.module.js');

module.exports = angular.module('spinnaker.serverGroup.details.gce.controller', [
  require('angular-ui-router'),
  require('../configure/serverGroupCommandBuilder.service.js'),
  require('core/application/modal/platformHealthOverride.directive.js'),
  SERVER_GROUP_WARNING_MESSAGE_SERVICE,
  SERVER_GROUP_READER_SERVICE,
  CONFIRMATION_MODAL_SERVICE,
  NETWORK_READ_SERVICE,
  SERVER_GROUP_WRITER_SERVICE,
  require('core/serverGroup/configure/common/runningExecutions.service.js'),
  require('./resize/resizeServerGroup.controller'),
  require('./rollback/rollbackServerGroup.controller'),
  require('./autoscalingPolicy/autoscalingPolicy.directive.js'),
  require('core/utils/selectOnDblClick.directive.js'),
  require('./autoscalingPolicy/addAutoscalingPolicyButton.component.js')
])
  .controller('gceServerGroupDetailsCtrl', function ($scope, $state, $templateCache, $interpolate, app, serverGroup,
                                                     gceServerGroupCommandBuilder, serverGroupReader, $uibModal, confirmationModalService, serverGroupWriter,
                                                     runningExecutionsService, serverGroupWarningMessageService, networkReader) {

    this.state = {
      loading: true
    };

    this.application = app;

    let extractServerGroupSummary = () => {
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
      var summary = extractServerGroupSummary();
      return serverGroupReader.getServerGroup(app.name, serverGroup.accountId, serverGroup.region, serverGroup.name).then((details) => {
        cancelLoader();

        angular.extend(details, summary);
        // it's possible the summary was not found because the clusters are still loading
        details.account = serverGroup.accountId;

        this.serverGroup = details;
        this.runningExecutions = () => {
          return runningExecutionsService.filterRunningExecutions(this.serverGroup.executions);
        };

        if (!_.isEmpty(this.serverGroup)) {
          if (details.securityGroups) {
            this.securityGroups = _.chain(details.securityGroups).map((id) => {
              return _.find(app.securityGroups.data, { 'accountName': serverGroup.accountId, 'region': 'global', 'id': id }) ||
                _.find(app.securityGroups.data, { 'accountName': serverGroup.accountId, 'region': 'global', 'name': id });
            }).compact().value();
          }

          this.serverGroup.zones.sort();

          this.serverGroup.network = getNetwork();
          retrieveSubnet();

          var pathSegments = this.serverGroup.launchConfig.instanceTemplate.selfLink.split('/');
          var projectId = pathSegments[pathSegments.indexOf('projects') + 1];
          this.serverGroup.logsLink =
            'https://console.developers.google.com/project/' + projectId + '/logs?advancedFilter=resource.type=(gce_instance_group_manager OR gce_instance OR gce_autoscaler)%0A\"' + this.serverGroup.name + '\"';

          findStartupScript();
          prepareDiskDescriptions();
          prepareAvailabilityPolicies();
          prepareAutoHealingPolicy();
          prepareAuthScopes();
          prepareCurrentActions();
          augmentTagsWithHelp();
        } else {
          autoClose();
        }
      },
        autoClose
      );
    };

    let findStartupScript = () => {
      if (_.has(this.serverGroup, 'launchConfig.instanceTemplate.properties.metadata.items')) {
        let metadataItems = this.serverGroup.launchConfig.instanceTemplate.properties.metadata.items;
        let startupScriptItem = _.find(metadataItems, metadataItem => {
          return metadataItem.key === 'startup-script';
        });

        if (startupScriptItem) {
          this.serverGroup.startupScript = startupScriptItem.value;
        }
      }
    };

    let prepareDiskDescriptions = () => {
      if (_.has(this.serverGroup, 'launchConfig.instanceTemplate.properties.disks')) {
        let diskDescriptions = [];

        this.serverGroup.launchConfig.instanceTemplate.properties.disks.forEach(disk => {
          let diskLabel = disk.initializeParams.diskType + ':' + disk.initializeParams.diskSizeGb;
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
              finalLabel: translateDiskType(disk.initializeParams.diskType) + ': ' + disk.initializeParams.diskSizeGb + 'GB',
            });
          }
        });

        this.serverGroup.diskDescriptions = diskDescriptions;
      }
    };

    let prepareAvailabilityPolicies = () => {
      if (_.has(this.serverGroup, 'launchConfig.instanceTemplate.properties.scheduling')) {
        let scheduling = this.serverGroup.launchConfig.instanceTemplate.properties.scheduling;

        this.serverGroup.availabilityPolicies = {
          preemptibility: scheduling.preemptible ? 'On' : 'Off',
          automaticRestart: scheduling.automaticRestart ? 'On' : 'Off',
          onHostMaintenance: scheduling.onHostMaintenance === 'MIGRATE' ? 'Migrate' : 'Terminate',
        };
      }
    };

    let prepareAutoHealingPolicy = () => {
      if (this.serverGroup.autoHealingPolicy) {
        let autoHealingPolicy = this.serverGroup.autoHealingPolicy;
        let healthCheckUrl = autoHealingPolicy.healthCheck;

        this.serverGroup.autoHealingPolicyHealthCheck = healthCheckUrl ? _.last(healthCheckUrl.split('/')) : null;
        this.serverGroup.initialDelaySec = autoHealingPolicy.initialDelaySec;

        if (autoHealingPolicy.maxUnavailable) {
          if (typeof autoHealingPolicy.maxUnavailable.percent === 'number') {
            this.serverGroup.maxUnavailable = autoHealingPolicy.maxUnavailable.percent + '%';
          } else {
            this.serverGroup.maxUnavailable = autoHealingPolicy.maxUnavailable.fixed + ' instances';
          }
        }
      }
    };

    let prepareAuthScopes = () => {
      if (_.has(this.serverGroup, 'launchConfig.instanceTemplate.properties.serviceAccounts')) {
        let serviceAccounts = this.serverGroup.launchConfig.instanceTemplate.properties.serviceAccounts;
        if (serviceAccounts.length) {
          let serviceAccount = this.serverGroup.launchConfig.instanceTemplate.properties.serviceAccounts[0];

          this.serverGroup.serviceAccountEmail = serviceAccount.email;
          this.serverGroup.authScopes = _.map(serviceAccount.scopes, authScope => {
            return authScope.replace('https://www.googleapis.com/auth/', '');
          });
        }
      }
    };

    let prepareCurrentActions = () => {
      if (this.serverGroup.currentActions) {
        this.serverGroup.currentActionsSummary = [];

        _.forOwn(this.serverGroup.currentActions, (value, key) => {
          if (key !== 'none' && value) {
            this.serverGroup.currentActionsSummary.push({action: _.startCase(key), count: value});
          }
        });

        if (!this.serverGroup.currentActionsSummary.length) {
          delete this.serverGroup.currentActionsSummary;
        }
      }
    };

    let translateDiskType = (diskType) => {
      if (diskType === 'pd-ssd') {
        return 'Persistent SSD';
      } else if (diskType === 'local-ssd') {
        return 'Local SSD';
      } else {
        return 'Persistent Std';
      }
    };

    let augmentTagsWithHelp = () => {
      if (_.has(this.serverGroup, 'launchConfig.instanceTemplate.properties.tags.items') && this.securityGroups) {
        let helpMap = {};

        this.serverGroup.launchConfig.instanceTemplate.properties.tags.items.forEach(tag => {
          let securityGroupsMatches = _.filter(this.securityGroups, securityGroup => _.includes(securityGroup.targetTags, tag));
          let securityGroupMatchNames = _.map(securityGroupsMatches, 'name');

          if (!_.isEmpty(securityGroupMatchNames)) {
            let groupOrGroups = securityGroupMatchNames.length > 1 ? 'groups' : 'group';

            helpMap[tag] = 'This tag associates this server group with security ' + groupOrGroups + ' <em>' + securityGroupMatchNames.join(', ') + '</em>.';
          }
        });

        this.serverGroup.launchConfig.instanceTemplate.properties.tags.helpMap = helpMap;
      }
    };

    let getNetwork = () => {
      let networkUrl = _.get(this.serverGroup, 'launchConfig.instanceTemplate.properties.networkInterfaces[0].network');
      return networkUrl ? _.last(networkUrl.split('/')) : null;
    };

    let retrieveSubnet = () => {
      networkReader.listNetworksByProvider('gce').then((networks) => {
        let autoCreateSubnets = _.chain(networks)
          .filter({ account: this.serverGroup.account, name: this.serverGroup.network })
          .map('autoCreateSubnets')
          .head()
          .value();

        if (autoCreateSubnets) {
          this.serverGroup.subnet = '(Auto-select)';
        } else {
          let subnetUrl = _.get(this.serverGroup, 'launchConfig.instanceTemplate.properties.networkInterfaces[0].subnetwork');
          this.serverGroup.subnet = subnetUrl ? _.last(subnetUrl.split('/')) : null;
        }
      });
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
        provider: 'gce',
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
        askForReason: true,
        platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Google',
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

      if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
        confirmationModalParams.interestingHealthProviderNames = ['Google'];
      }

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

      var submitMethod = (params) => serverGroupWriter.disableServerGroup(serverGroup, app, params);

      var confirmationModalParams = {
        header: 'Really disable ' + serverGroup.name + '?',
        buttonText: 'Disable ' + serverGroup.name,
        provider: 'gce',
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Google',
        submitMethod: submitMethod,
        askForReason: true,
      };

      if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
        confirmationModalParams.interestingHealthProviderNames = ['Google'];
      }

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.enableServerGroup = () => {
      var serverGroup = this.serverGroup;

      var taskMonitor = {
        application: app,
        title: 'Enabling ' + serverGroup.name,
      };

      var submitMethod = (params) => serverGroupWriter.enableServerGroup(serverGroup, app, params);

      var confirmationModalParams = {
        header: 'Really enable ' + serverGroup.name + '?',
        buttonText: 'Enable ' + serverGroup.name,
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Google',
        submitMethod: submitMethod,
        askForReason: true,
      };

      if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
        confirmationModalParams.interestingHealthProviderNames = ['Google'];
      }

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.rollbackServerGroup = () => {
      $uibModal.open({
        templateUrl: require('./rollback/rollbackServerGroup.html'),
        controller: 'gceRollbackServerGroupCtrl as ctrl',
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

    this.resizeServerGroup = () => {
      $uibModal.open({
        templateUrl: require('./resize/resizeServerGroup.html'),
        controller: 'gceResizeServerGroupCtrl as ctrl',
        resolve: {
          serverGroup: () => { return this.serverGroup; },
          application: () => { return app; }
        }
      });
    };

    this.cloneServerGroup = (serverGroup) => {
      $uibModal.open({
        templateUrl: require('../configure/wizard/serverGroupWizard.html'),
        controller: 'gceCloneServerGroupCtrl as ctrl',
        size: 'lg',
        resolve: {
          title: () => { return 'Clone ' + serverGroup.name; },
          application: () => { return app; },
          serverGroup: () => { return serverGroup; },
          serverGroupCommand: () => { return gceServerGroupCommandBuilder.buildServerGroupCommandFromExisting(app, serverGroup); },
        }
      });
    };

    this.showStartupScript = () => {
      $scope.userDataModalTitle = 'Startup Script';
      $scope.serverGroup = { name: this.serverGroup.name };
      $scope.userData = this.serverGroup.startupScript;
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

    this.truncateCommitHash = () => {
      if (this.serverGroup && this.serverGroup.buildInfo && this.serverGroup.buildInfo.commit) {
        return this.serverGroup.buildInfo.commit.substring(0, 8);
      }
      return null;
    };
  }
);
