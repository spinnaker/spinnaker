'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.securityGroup.azure.details.controller', [
  require('angular-ui-router'),
  require('../../../core/securityGroup/securityGroup.read.service.js'),
  require('../../../core/securityGroup/securityGroup.write.service.js'),
  require('../../../core/confirmationModal/confirmationModal.service.js'),
  require('../../../core/utils/lodash.js'),
  require('../../../core/insight/insightFilterState.model.js'),
  require('../clone/cloneSecurityGroup.controller.js'),
  require('../../../core/utils/selectOnDblClick.directive.js'),
])
  .controller('azureSecurityGroupDetailsCtrl', function ($scope, $state, resolvedSecurityGroup, app, InsightFilterStateModel,
                                                    confirmationModalService, securityGroupWriter, securityGroupReader,
                                                    $modal, _) {

    const application = app;
    const securityGroup = resolvedSecurityGroup;

    $scope.state = {
      loading: true
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

    function extractSecurityGroup() {
      return securityGroupReader.getSecurityGroupDetails(application, securityGroup.accountId, securityGroup.provider, securityGroup.region, securityGroup.vpcId, securityGroup.name).then(function (details) {
        $scope.state.loading = false;

        if (!details || _.isEmpty( details.plain())) {
          fourOhFour();
        } else {
          $scope.securityGroup = details;
        }
      },
      function() {
        fourOhFour();
      });
    }

    function fourOhFour() {
      $state.go('^');
    }

    extractSecurityGroup().then(() => {
      // If the user navigates away from the view before the initial extractSecurityGroup call completes,
      // do not bother subscribing to the autoRefreshStream
      if (!$scope.$$destroyed) {
        let refreshWatcher = app.autoRefreshStream.subscribe(extractSecurityGroup);
        $scope.$on('$destroy', () => refreshWatcher.dispose());
      }
    });

    this.editInboundRules = function editInboundRules() {
      $modal.open({
        templateUrl: require('../configure/editSecurityGroup.html'),
        controller: 'azureEditSecurityGroupCtrl as ctrl',
        resolve: {
          securityGroup: function() {
            return angular.copy($scope.securityGroup.plain());
          },
          application: function() { return application; }
        }
      });
    };


    this.cloneSecurityGroup = function cloneSecurityGroup() {
      $modal.open({
        templateUrl: require('../clone/cloneSecurityGroup.html'),
        controller: 'azureCloneSecurityGroupController as ctrl',
        resolve: {
          securityGroup: function() {
            var securityGroup = angular.copy($scope.securityGroup.plain());
            if(securityGroup.region) {
              securityGroup.regions = [securityGroup.region];
            }
            return securityGroup;
          },
          application: function() { return application; }
        }
      });
    };

    this.deleteSecurityGroup = function deleteSecurityGroup() {
      var taskMonitor = {
        application: application,
        title: 'Deleting ' + securityGroup.name,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true
      };

      var submitMethod = function () {
        return securityGroupWriter.deleteSecurityGroup(securityGroup, application, {
          cloudProvider: $scope.securityGroup.type,
          vpcId: $scope.securityGroup.vpcId,
        });
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + securityGroup.name + '?',
        buttonText: 'Delete ' + securityGroup.name,
        destructive: true,
        provider: 'azure',
        account: securityGroup.accountId,
        applicationName: application.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

  }
);
