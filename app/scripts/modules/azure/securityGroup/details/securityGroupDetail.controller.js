'use strict';

import _ from 'lodash';

import {CONFIRMATION_MODAL_SERVICE} from 'core/confirmationModal/confirmationModal.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.securityGroup.azure.details.controller', [
  require('angular-ui-router'),
  require('core/securityGroup/securityGroup.read.service.js'),
  require('../securityGroup.write.service.js'),
  CONFIRMATION_MODAL_SERVICE,
  require('core/insight/insightFilterState.model.js'),
  require('../clone/cloneSecurityGroup.controller.js'),
  require('core/utils/selectOnDblClick.directive.js'),
])
  .controller('azureSecurityGroupDetailsCtrl', function ($scope, $state, resolvedSecurityGroup, app, InsightFilterStateModel,
                                                    confirmationModalService, azureSecurityGroupWriter, securityGroupReader,
                                                    $uibModal) {

    const application = app;
    const securityGroup = resolvedSecurityGroup;

    $scope.state = {
      loading: true
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

    function extractSecurityGroup() {
      return securityGroupReader.getSecurityGroupDetails(application, securityGroup.accountId, securityGroup.provider, securityGroup.region, securityGroup.vpcId, securityGroup.name).then(function (details) {
        $scope.state.loading = false;

        if (!details || _.isEmpty( details)) {
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
      // do not bother subscribing to the refresh
      if (!$scope.$$destroyed) {
        app.securityGroups.onRefresh($scope, extractSecurityGroup);
      }
    });

    this.editInboundRules = function editInboundRules() {
      $uibModal.open({
        templateUrl: require('../configure/editSecurityGroup.html'),
        controller: 'azureEditSecurityGroupCtrl as ctrl',
        resolve: {
          securityGroup: function() {
            return angular.copy($scope.securityGroup);
          },
          application: function() { return application; }
        }
      });
    };


    this.cloneSecurityGroup = function cloneSecurityGroup() {
      $uibModal.open({
        templateUrl: require('../clone/cloneSecurityGroup.html'),
        controller: 'azureCloneSecurityGroupController as ctrl',
        resolve: {
          securityGroup: function() {
            var securityGroup = angular.copy($scope.securityGroup);
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
        $scope.securityGroup.type = 'deleteSecurityGroup';
        return azureSecurityGroupWriter.deleteSecurityGroup(securityGroup, application, {
          cloudProvider: 'azure',
          vpcId: $scope.securityGroup.vpcId,
        });
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + securityGroup.name + '?',
        buttonText: 'Delete ' + securityGroup.name,
        provider: 'azure',
        account: securityGroup.accountId,
        applicationName: application.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

    if (app.isStandalone) {
      // we still want the edit to refresh the security group details when the modal closes
      app.securityGroups = {
        refresh: extractSecurityGroup
      };
    }

  }
);
