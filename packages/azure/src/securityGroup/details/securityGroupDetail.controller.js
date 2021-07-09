'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';
import _ from 'lodash';

import { ConfirmationModalService, FirewallLabels, SECURITY_GROUP_READER } from '@spinnaker/core';

import { AZURE_SECURITYGROUP_CLONE_CLONESECURITYGROUP_CONTROLLER } from '../clone/cloneSecurityGroup.controller';
import { AZURE_SECURITYGROUP_SECURITYGROUP_WRITE_SERVICE } from '../securityGroup.write.service';

export const AZURE_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER =
  'spinnaker.azure.securityGroup.azure.details.controller';
export const name = AZURE_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER; // for backwards compatibility
angular
  .module(AZURE_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER, [
    UIROUTER_ANGULARJS,
    SECURITY_GROUP_READER,
    AZURE_SECURITYGROUP_SECURITYGROUP_WRITE_SERVICE,
    AZURE_SECURITYGROUP_CLONE_CLONESECURITYGROUP_CONTROLLER,
  ])
  .controller('azureSecurityGroupDetailsCtrl', [
    '$scope',
    '$state',
    'resolvedSecurityGroup',
    'app',
    'azureSecurityGroupWriter',
    'securityGroupReader',
    '$uibModal',
    function ($scope, $state, resolvedSecurityGroup, app, azureSecurityGroupWriter, securityGroupReader, $uibModal) {
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
            function (details) {
              $scope.state.loading = false;

              if (!details || _.isEmpty(details)) {
                fourOhFour();
              } else {
                $scope.securityGroup = details;
              }
            },
            function () {
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
            securityGroup: function () {
              return angular.copy($scope.securityGroup);
            },
            application: function () {
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
            securityGroup: function () {
              const securityGroup = angular.copy($scope.securityGroup);
              if (securityGroup.region) {
                securityGroup.regions = [securityGroup.region];
              }
              return securityGroup;
            },
            application: function () {
              return application;
            },
          },
        });
      };

      this.deleteSecurityGroup = function deleteSecurityGroup() {
        const taskMonitor = {
          application: application,
          title: 'Deleting ' + securityGroup.name,
        };

        const submitMethod = function () {
          $scope.securityGroup.type = 'deleteSecurityGroup';
          return azureSecurityGroupWriter.deleteSecurityGroup(securityGroup, application, {
            cloudProvider: 'azure',
            vpcId: $scope.securityGroup.vpcId,
          });
        };

        ConfirmationModalService.confirm({
          header: 'Really delete ' + securityGroup.name + '?',
          buttonText: 'Delete ' + securityGroup.name,
          account: securityGroup.accountId,
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
