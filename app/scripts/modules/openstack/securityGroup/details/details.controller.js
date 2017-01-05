'use strict';

import _ from 'lodash';

import {CLOUD_PROVIDER_REGISTRY} from 'core/cloudProvider/cloudProvider.registry';
import {CONFIRMATION_MODAL_SERVICE} from 'core/confirmationModal/confirmationModal.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.securityGroup.openstack.details.controller', [
  require('angular-ui-router'),
  require('core/securityGroup/securityGroup.read.service.js'),
  require('core/securityGroup/securityGroup.write.service.js'),
  CONFIRMATION_MODAL_SERVICE,
  require('core/insight/insightFilterState.model.js'),
  require('core/utils/selectOnDblClick.directive.js'),
  CLOUD_PROVIDER_REGISTRY,
])
  .controller('openstackSecurityGroupDetailsController', function ($scope, $state, resolvedSecurityGroup, app, InsightFilterStateModel,
                                                       confirmationModalService, securityGroupWriter, securityGroupReader,
                                                       $uibModal, cloudProviderRegistry) {

      const application = app;
      const securityGroup = resolvedSecurityGroup;

      // needed for standalone instances
      $scope.detailsTemplateUrl = cloudProviderRegistry.getValue('openstack', 'securityGroup.detailsTemplateUrl');

      $scope.state = {
        loading: true,
        standalone: app.isStandalone,
      };

      $scope.InsightFilterStateModel = InsightFilterStateModel;

      function extractSecurityGroup() {
        return securityGroupReader.getSecurityGroupDetails(application, securityGroup.accountId, securityGroup.provider, securityGroup.region, securityGroup.vpcId, securityGroup.name).then(function (details) {
            $scope.state.loading = false;

            if (!details || _.isEmpty(details)) {
              fourOhFour();
            } else {
              $scope.securityGroup = details;
            }
          },
          fourOhFour
        );
      }

      function fourOhFour() {
        if ($scope.$$destroyed) {
          return;
        }
        $state.params.allowModalToStayOpen = true;
        $state.go('^', null, {location: 'replace'});
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
          return securityGroupWriter.deleteSecurityGroup(_.omit(securityGroup,'accountId'), application, {
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
