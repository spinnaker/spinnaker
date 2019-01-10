'use strict';

const angular = require('angular');
import _ from 'lodash';

import {
  CloudProviderRegistry,
  CONFIRMATION_MODAL_SERVICE,
  FirewallLabels,
  SECURITY_GROUP_READER,
  SecurityGroupWriter,
} from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.securityGroup.openstack.details.controller', [
    require('@uirouter/angularjs').default,
    CONFIRMATION_MODAL_SERVICE,
    SECURITY_GROUP_READER,
  ])
  .controller('openstackSecurityGroupDetailsController', function(
    $scope,
    $state,
    resolvedSecurityGroup,
    app,
    confirmationModalService,
    securityGroupReader,
    $uibModal,
  ) {
    const application = app;
    const securityGroup = resolvedSecurityGroup;

    // needed for standalone instances
    $scope.detailsTemplateUrl = CloudProviderRegistry.getValue('openstack', 'securityGroup.detailsTemplateUrl');

    $scope.firewallLabel = FirewallLabels.get('Firewall');

    $scope.state = {
      loading: true,
      standalone: app.isStandalone,
    };

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
        .then(function(details) {
          $scope.state.loading = false;

          if (!details || _.isEmpty(details)) {
            fourOhFour();
          } else {
            $scope.securityGroup = details;
          }
        }, fourOhFour);
    }

    function fourOhFour() {
      if ($scope.$$destroyed) {
        return;
      }
      $state.go('^', { allowModalToStayOpen: true }, { location: 'replace' });
    }

    extractSecurityGroup().then(() => {
      // If the user navigates away from the view before the initial extractSecurityGroup call completes,
      // do not bother subscribing to the refresh
      if (!$scope.$$destroyed && !app.isStandalone) {
        app.securityGroups.onRefresh($scope, extractSecurityGroup);
      }
    });

    this.editSecurityGroup = function editSecurityGroup() {
      $uibModal.open({
        templateUrl: require('../configure/wizard/editWizard.html'),
        controller: 'openstackUpsertSecurityGroupController as ctrl',
        size: 'lg',
        resolve: {
          securityGroup: function() {
            var securityGroup = angular.copy($scope.securityGroup);
            securityGroup.edit = true;
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
        return SecurityGroupWriter.deleteSecurityGroup(_.omit(securityGroup, 'accountId'), application, {
          cloudProvider: securityGroup.provider,
          id: $scope.securityGroup.id,
          region: securityGroup.region,
          account: securityGroup.accountId,
        });
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + securityGroup.name + '?',
        buttonText: 'Delete ' + securityGroup.name,
        provider: 'openstack',
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
  });
