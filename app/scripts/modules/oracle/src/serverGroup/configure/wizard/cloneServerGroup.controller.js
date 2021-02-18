'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';

import { FirewallLabels, ModalWizard, TaskMonitor } from '@spinnaker/core';

export const ORACLE_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_CONTROLLER =
  'spinnaker.oracle.serverGroup.configure.cloneServerGroup';
export const name = ORACLE_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_CONTROLLER; // for backwards compatibility
module(ORACLE_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_CONTROLLER, [UIROUTER_ANGULARJS]).controller(
  'oracleCloneServerGroupCtrl',
  [
    '$scope',
    '$uibModalInstance',
    '$q',
    'application',
    'serverGroupWriter',
    'serverGroupCommand',
    'oracleServerGroupConfigurationService',
    'title',
    function (
      $scope,
      $uibModalInstance,
      $q,
      application,
      serverGroupWriter,
      serverGroupCommand,
      oracleServerGroupConfigurationService,
      title,
    ) {
      $scope.title = title;

      $scope.state = {
        loaded: false,
        requiresTemplateSelection: !!serverGroupCommand.viewState.requiresTemplateSelection,
      };

      $scope.command = serverGroupCommand;
      $scope.application = application;

      $scope.pages = {
        basicSettings: require('./basicSettings/basicSettings.html'),
        instanceType: require('./instanceType/instanceType.html'),
        capacity: require('./capacity/capacity.html'),
        loadBalancers: require('./loadBalancers/loadBalancers.html'),
        networkSettings: require('./network/networkSettings.html'),
        templateSelection: require('./templateSelection/templateSelection.html'),
      };

      this.templateSelectionText = {
        copied: [
          'account, region, subnet, cluster name (stack, details)',
          'load balancers',
          FirewallLabels.get('firewalls'),
          'instance type',
          'all fields on the Advanced Settings page',
        ],
        notCopied: [],
      };

      if (!$scope.command.viewState.disableStrategySelection) {
        this.templateSelectionText.notCopied.push(
          'the deployment strategy (if any) used to deploy the most recent server group',
        );
      }

      $scope.taskMonitor = new TaskMonitor({
        application: application,
        title: 'Creating your server group',
        modalInstance: $uibModalInstance,
      });

      function configureCommand() {
        oracleServerGroupConfigurationService.configureCommand(application, serverGroupCommand).then(function () {
          $scope.state.loaded = true;
        });
      }

      this.isValid = function () {
        return $scope.command && $scope.form.$valid && ModalWizard.isComplete();
      };

      this.showSubmitButton = function () {
        return ModalWizard.allPagesVisited();
      };

      this.cancel = function () {
        $uibModalInstance.dismiss();
      };

      this.submit = function () {
        if ($scope.command.viewState.mode === 'editPipeline' || $scope.command.viewState.mode === 'createPipeline') {
          return $uibModalInstance.close($scope.command);
        }
        $scope.taskMonitor.submit(function () {
          return serverGroupWriter.cloneServerGroup($scope.command, application);
        });
      };

      configureCommand();

      this.templateSelected = () => {
        $scope.state.requiresTemplateSelection = false;
        configureCommand();
      };
    },
  ],
);
