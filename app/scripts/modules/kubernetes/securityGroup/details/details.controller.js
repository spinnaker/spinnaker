'use strict';

import _ from 'lodash';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

module.exports = angular.module('spinnaker.securityGroup.kubernetes.details.controller', [
  require('angular-ui-router'),
  ACCOUNT_SERVICE,
  require('core/securityGroup/securityGroup.read.service.js'),
  require('core/securityGroup/securityGroup.write.service.js'),
  require('core/confirmationModal/confirmationModal.service.js'),
  require('core/insight/insightFilterState.model.js'),
  require('core/utils/selectOnDblClick.directive.js'),
  require('core/cloudProvider/cloudProvider.registry.js'),
])
  .controller('kubernetesSecurityGroupDetailsController', function ($scope, $state, resolvedSecurityGroup, accountService, app, InsightFilterStateModel,
                                                    confirmationModalService, securityGroupWriter, securityGroupReader,
                                                    $uibModal, cloudProviderRegistry) {

    const application = app;
    const securityGroup = resolvedSecurityGroup;

    // needed for standalone instances
    $scope.detailsTemplateUrl = cloudProviderRegistry.getValue('kubernetes', 'securityGroup.detailsTemplateUrl');

    $scope.state = {
      loading: true,
      standalone: app.isStandalone,
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

    function extractSecurityGroup() {
      return securityGroupReader.getSecurityGroupDetails(application, securityGroup.accountId, securityGroup.provider, securityGroup.region, securityGroup.vpcId, securityGroup.name).then(function (details) {
        $scope.state.loading = false;

        if (!details || _.isEmpty(details)) {
          autoClose();
        } else {
          $scope.securityGroup = details;
        }
      },
        autoClose
      );
    }

    this.showYaml = function showYaml() {
      $scope.userDataModalTitle = 'Ingress YAML';
      $scope.userData = $scope.securityGroup.yaml;
      $uibModal.open({
        templateUrl: require('core/serverGroup/details/userData.html'),
        controller: 'CloseableModalCtrl',
        scope: $scope
      });
    };

    function autoClose() {
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
        controller: 'kubernetesUpsertSecurityGroupController as ctrl',
        size: 'lg',
        resolve: {
          securityGroup: function() {
            var securityGroup = angular.copy($scope.securityGroup.description);
            securityGroup.account = $scope.securityGroup.account;
            securityGroup.edit = true;
            return securityGroup;
          },
          application: function() {
            return application;
          }
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
          securityGroupName: securityGroup.name,
        });
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + securityGroup.name + '?',
        buttonText: 'Delete ' + securityGroup.name,
        provider: 'kubernetes',
        account: securityGroup.accountId,
        applicationName: application.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

    if (app.isStandalone) {
      app.securityGroups = {
        refresh: extractSecurityGroup
      };
    }
  });
