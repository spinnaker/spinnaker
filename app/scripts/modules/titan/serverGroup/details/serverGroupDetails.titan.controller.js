'use strict';
/* jshint camelcase:false */

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.titan.controller', [
  require('angular-ui-router'),
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
  .controller('titanServerGroupDetailsCtrl', function ($scope, $state, $templateCache, $interpolate, app, serverGroup, InsightFilterStateModel,
                                                     titanServerGroupCommandBuilder, serverGroupReader, $uibModal, confirmationModalService, _, serverGroupWriter,
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
        application: application,
        title: 'Destroying ' + serverGroup.name,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true,
        katoPhaseToMonitor: 'DESTROY_TITAN_SERVER_GROUP'
      };

      var submitMethod = function () {
        return serverGroupWriter.destroyServerGroup(serverGroup, application, {
          cloudProvider: 'titan',
          serverGroupName: serverGroup.name,
          region: serverGroup.region,
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
        provider: 'titan',
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

      var submitMethod = function () {
        return serverGroupWriter.disableServerGroup(serverGroup, application, {
          cloudProvider: 'titan',
          serverGroupName: serverGroup.name,
          region: serverGroup.region,
          zone: serverGroup.zones[0],
        });
      };

      confirmationModalService.confirm({
        header: 'Really disable ' + serverGroup.name + '?',
        buttonText: 'Disable ' + serverGroup.name,
        destructive: true,
        provider: 'titan',
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });

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
          cloudProvider: 'titan',
          serverGroupName: serverGroup.name,
          region: serverGroup.region,
          zone: serverGroup.zones[0],
        });
      };

      confirmationModalService.confirm({
        header: 'Really enable ' + serverGroup.name + '?',
        buttonText: 'Enable ' + serverGroup.name,
        destructive: false,
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });

    };

    this.resizeServerGroup = function resizeServerGroup() {
      $uibModal.open({
        templateUrl: require('./resize/resizeServerGroup.html'),
        controller: 'titanResizeServerGroupCtrl as ctrl',
        resolve: {
          serverGroup: function() { return $scope.serverGroup; },
          application: function() { return application; }
        }
      });
    };

    this.cloneServerGroup = function cloneServerGroup(serverGroup) {
      $uibModal.open({
        templateUrl: require('../configure/wizard/serverGroupWizard.html'),
        controller: 'titanCloneServerGroupCtrl as ctrl',
        resolve: {
          title: function() { return 'Clone ' + serverGroup.name; },
          application: function() { return application; },
          serverGroup: function() { return serverGroup; },
          serverGroupCommand: function() { return titanServerGroupCommandBuilder.buildServerGroupCommandFromExisting(application, serverGroup); },
        }
      });
    };

  }
);
