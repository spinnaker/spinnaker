'use strict';

const angular = require('angular');
import _ from 'lodash';

import { CONFIRMATION_MODAL_SERVICE, SECURITY_GROUP_READER, FirewallLabels } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.azure.securityGroup.azure.details.controller', [
    require('@uirouter/angularjs').default,
    SECURITY_GROUP_READER,
    require('../securityGroup.write.service').name,
    CONFIRMATION_MODAL_SERVICE,
    require('../clone/cloneSecurityGroup.controller').name,
  ])
  .controller('azureSecurityGroupDetailsCtrl', [
    '$scope',
    '$state',
    'resolvedSecurityGroup',
    'app',
    'confirmationModalService',
    'azureSecurityGroupWriter',
    'securityGroupReader',
    '$uibModal',
    function(
      $scope,
      $state,
      resolvedSecurityGroup,
      app,
      confirmationModalService,
      azureSecurityGroupWriter,
      securityGroupReader,
      $uibModal,
    ) {
      const application = app;
      const securityGroup = resolvedSecurityGroup;

      $scope.state = {
        loading: true,
      };

      $scope.firewallLabel = FirewallLabels.get('Firewall');

      function extractSecurityGroup() {
        return securityGroupReader
          .getSecurityGroupDetails(
            application,
            securityGroup.accountId,
            securityGroup.provider,
            securityGroup.region,
            securityGroup.vpcId,
            securityGroup.name,
          )
          .then(
            function(details) {
              $scope.state.loading = false;

              if (!details || _.isEmpty(details)) {
                fourOhFour();
              } else {
                $scope.securityGroup = details;
              }
            },
            function() {
              fourOhFour();
            },
          );
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
            application: function() {
              return application;
            },
          },
        });
      };

      this.cloneSecurityGroup = function cloneSecurityGroup() {
        $uibModal.open({
          templateUrl: require('../clone/cloneSecurityGroup.html'),
          controller: 'azureCloneSecurityGroupController as ctrl',
          resolve: {
            securityGroup: function() {
              var securityGroup = angular.copy($scope.securityGroup);
              if (securityGroup.region) {
                securityGroup.regions = [securityGroup.region];
              }
              return securityGroup;
            },
            application: function() {
              return application;
            },
          },
        });
      };

      this.deleteSecurityGroup = function deleteSecurityGroup() {
        var taskMonitor = {
          application: application,
          title: 'Deleting ' + securityGroup.name,
        };

        var submitMethod = function() {
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
          submitMethod: submitMethod,
        });
      };

      if (app.isStandalone) {
        // we still want the edit to refresh the firewall details when the modal closes
        app.securityGroups = {
          refresh: extractSecurityGroup,
        };
      }
    },
  ]);
