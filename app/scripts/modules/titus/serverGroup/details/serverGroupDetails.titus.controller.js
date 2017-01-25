'use strict';

let angular = require('angular');
import _ from 'lodash';

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {CONFIRMATION_MODAL_SERVICE} from 'core/confirmationModal/confirmationModal.service';
import {SERVER_GROUP_READER} from 'core/serverGroup/serverGroupReader.service';
import {SERVER_GROUP_WRITER} from 'core/serverGroup/serverGroupWriter.service';
import {SERVER_GROUP_WARNING_MESSAGE_SERVICE} from 'core/serverGroup/details/serverGroupWarningMessage.service';
import {RUNNING_TASKS_DETAILS_COMPONENT} from 'core/serverGroup/details/runningTasks.component';
import {CLUSTER_TARGET_BUILDER} from 'core/entityTag/clusterTargetBuilder.service';

module.exports = angular.module('spinnaker.serverGroup.details.titus.controller', [
  require('angular-ui-router'),
  ACCOUNT_SERVICE,
  require('../configure/ServerGroupCommandBuilder.js'),
  SERVER_GROUP_WARNING_MESSAGE_SERVICE,
  SERVER_GROUP_READER,
  CONFIRMATION_MODAL_SERVICE,
  SERVER_GROUP_WRITER,
  RUNNING_TASKS_DETAILS_COMPONENT,
  require('./resize/resizeServerGroup.controller'),
  require('core/modal/closeable/closeable.modal.controller.js'),
  require('core/utils/selectOnDblClick.directive.js'),
  CLUSTER_TARGET_BUILDER
])
  .controller('titusServerGroupDetailsCtrl', function ($scope, $state, $templateCache, $interpolate, app, serverGroup,
                                                       titusServerGroupCommandBuilder, serverGroupReader, $uibModal,
                                                       confirmationModalService, serverGroupWriter, clusterTargetBuilder,
                                                       serverGroupWarningMessageService, accountService) {

    let application = app;

    $scope.state = {
      loading: true
    };

    function extractServerGroupSummary () {
      var summary = _.find(application.serverGroups.data, function (toCheck) {
        return toCheck.name === serverGroup.name && toCheck.account === serverGroup.accountId && toCheck.region === serverGroup.region;
      });
      return summary;
    }

    function retrieveServerGroup() {
      var summary = extractServerGroupSummary();
      return serverGroupReader.getServerGroup(application.name, serverGroup.accountId, serverGroup.region, serverGroup.name).then(function(details) {
        cancelLoader();

        // it's possible the summary was not found because the clusters are still loading
        details.account = serverGroup.accountId;

        accountService.getAccountDetails(details.account).then((accountDetails) => {
          details.apiEndpoint = _.filter(accountDetails.regions, {name: details.region})[0].endpoint;
        });

        angular.extend(details, summary);

        $scope.serverGroup = details;

        if (!_.isEmpty($scope.serverGroup)) {
          if (details.securityGroups) {
            $scope.securityGroups = _.chain(details.securityGroups).map(function(id) {
              return _.find(application.securityGroups.data, { 'accountName': serverGroup.accountId, 'region': 'global', 'id': id }) ||
                _.find(application.securityGroups.data, { 'accountName': serverGroup.accountId, 'region': 'global', 'name': id });
            }).compact().value();
          }
          configureEntityTagTargets();
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
      // do not bother subscribing to the refresh
      if (!$scope.$$destroyed) {
        app.serverGroups.onRefresh($scope, retrieveServerGroup);
      }
    });

    let configureEntityTagTargets = () => {
      this.entityTagTargets = clusterTargetBuilder.buildClusterTargets(this.serverGroup);
    };

    this.destroyServerGroup = function destroyServerGroup() {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: application,
        title: 'Destroying ' + serverGroup.name,
      };

      var submitMethod = function () {
        return serverGroupWriter.destroyServerGroup(serverGroup, application, {
          cloudProvider: 'titus',
          serverGroupName: serverGroup.name,
          region: serverGroup.region,
        });
      };

      var stateParams = {
        name: serverGroup.name,
        accountId: serverGroup.account,
        region: serverGroup.region
      };

      var confirmationModalParams = {
        header: 'Really destroy ' + serverGroup.name + '?',
        buttonText: 'Destroy ' + serverGroup.name,
        provider: 'titus',
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Titus',
        submitMethod: submitMethod,
        body: this.getBodyTemplate(serverGroup, application),
        onTaskComplete: function () {
          if ($state.includes('**.serverGroup', stateParams)) {
            $state.go('^');
          }
        },
      };

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.getBodyTemplate = (serverGroup, application) => {
      if (this.isLastServerGroupInRegion(serverGroup, application)) {
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

      var submitMethod = function () {
        return serverGroupWriter.disableServerGroup(serverGroup, application, {
          cloudProvider: 'titus',
          serverGroupName: serverGroup.name,
          region: serverGroup.region,
          zone: serverGroup.zones[0],
        });
      };

      var confirmationModalParams = {
        header: 'Really disable ' + serverGroup.name + '?',
        buttonText: 'Disable ' + serverGroup.name,
        provider: 'titus',
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Titus',
        submitMethod: submitMethod
      };

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.enableServerGroup = function enableServerGroup() {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: application,
        title: 'Enabling ' + serverGroup.name,
      };

      var submitMethod = function () {
        return serverGroupWriter.enableServerGroup(serverGroup, application, {
          cloudProvider: 'titus',
          serverGroupName: serverGroup.name,
          region: serverGroup.region,
          zone: serverGroup.zones[0],
        });
      };

      var confirmationModalParams = {
        header: 'Really enable ' + serverGroup.name + '?',
        buttonText: 'Enable ' + serverGroup.name,
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Titus',
        submitMethod: submitMethod
      };

      confirmationModalService.confirm(confirmationModalParams);

    };

    this.resizeServerGroup = function resizeServerGroup() {
      $uibModal.open({
        templateUrl: require('./resize/resizeServerGroup.html'),
        controller: 'titusResizeServerGroupCtrl as ctrl',
        resolve: {
          serverGroup: function() { return $scope.serverGroup; },
          application: function() { return application; }
        }
      });
    };

    this.cloneServerGroup = function cloneServerGroup() {
      var serverGroup = $scope.serverGroup;
      $uibModal.open({
        templateUrl: require('../configure/wizard/serverGroupWizard.html'),
        controller: 'titusCloneServerGroupCtrl as ctrl',
        size: 'lg',
        resolve: {
          title: function() { return 'Clone ' + serverGroup.name; },
          application: function() { return application; },
          serverGroup: function() { return serverGroup; },
          serverGroupCommand: function() { return titusServerGroupCommandBuilder.buildServerGroupCommandFromExisting(application, serverGroup); },
        }
      });
    };
  }
);
