'use strict';
/* jshint camelcase:false */

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.titus.controller', [
  require('angular-ui-router'),
  require('../../../core/account/account.service.js'),
  require('../configure/ServerGroupCommandBuilder.js'),
  require('../../../core/serverGroup/serverGroup.read.service.js'),
  require('../../../core/serverGroup/details/serverGroupWarningMessage.service.js'),
  require('../../../core/confirmationModal/confirmationModal.service.js'),
  require('../../../core/serverGroup/serverGroup.write.service.js'),
  require('../../../core/serverGroup/configure/common/runningExecutions.service.js'),
  require('../../../core/utils/lodash.js'),
  require('../../../core/insight/insightFilterState.model.js'),
  require('./resize/resizeServerGroup.controller'),
  require('../../../core/modal/closeable/closeable.modal.controller.js'),
  require('../../../core/utils/selectOnDblClick.directive.js'),
])
  .controller('titusServerGroupDetailsCtrl', function ($scope, $state, $templateCache, $interpolate, app, serverGroup, InsightFilterStateModel,
                                                     titusServerGroupCommandBuilder, serverGroupReader, $uibModal, confirmationModalService, _, serverGroupWriter,
                                                       runningExecutionsService, serverGroupWarningMessageService, accountService) {

    let application = app;

    $scope.state = {
      loading: true
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

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
          details.apiEndpoint = _.where(accountDetails.regions, {name: details.region})[0].endpoint;
        });

        angular.extend(details, summary);

        $scope.serverGroup = details;
        $scope.runningExecutions = function() {
          return runningExecutionsService.filterRunningExecutions($scope.serverGroup.executions);
        };

        if (!_.isEmpty($scope.serverGroup)) {
          if (details.securityGroups) {
            $scope.securityGroups = _(details.securityGroups).map(function(id) {
              return _.find(application.securityGroups.data, { 'accountName': serverGroup.accountId, 'region': 'global', 'id': id }) ||
                _.find(application.securityGroups.data, { 'accountName': serverGroup.accountId, 'region': 'global', 'name': id });
            }).compact().value();
          }

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

    this.destroyServerGroup = function destroyServerGroup() {
      var serverGroup = $scope.serverGroup;

      var taskMonitor = {
        application: application,
        title: 'Destroying ' + serverGroup.name,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true,
        katoPhaseToMonitor: 'DESTROY_TITUS_SERVER_GROUP'
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
        onApplicationRefresh: function () {
          if ($state.includes('**.serverGroup', stateParams)) {
            $state.go('^');
          }
        }
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
        forceRefreshMessage: 'Refreshing application...',
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
