'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.oraclebmcs.serverGroup.configure.cloneServerGroup', [require('@uirouter/angularjs').default])
  .controller('oraclebmcsCloneServerGroupCtrl', function(
    $scope,
    $uibModalInstance,
    $q,
    application,
    taskMonitorBuilder,
    serverGroupWriter,
    serverGroupCommand,
    oraclebmcsServerGroupConfigurationService,
    v2modalWizardService,
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
      templateSelection: require('./templateSelection/templateSelection.html'),
    };

    this.templateSelectionText = {
      copied: [
        'account, region, subnet, cluster name (stack, details)',
        'load balancers',
        'security groups',
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

    $scope.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      application: application,
      title: 'Creating your server group',
      modalInstance: $uibModalInstance,
    });

    function configureCommand() {
      oraclebmcsServerGroupConfigurationService.configureCommand(application, serverGroupCommand).then(function() {
        $scope.state.loaded = true;
      });
    }

    this.isValid = function() {
      return $scope.command && $scope.form.$valid && v2modalWizardService.isComplete();
    };

    this.showSubmitButton = function() {
      return v2modalWizardService.allPagesVisited();
    };

    this.cancel = function() {
      $uibModalInstance.dismiss();
    };

    this.submit = function() {
      if ($scope.command.viewState.mode === 'editPipeline' || $scope.command.viewState.mode === 'createPipeline') {
        return $uibModalInstance.close($scope.command);
      }
      $scope.taskMonitor.submit(function() {
        return serverGroupWriter.cloneServerGroup($scope.command, application);
      });
    };

    configureCommand();

    this.templateSelected = () => {
      $scope.state.requiresTemplateSelection = false;
      configureCommand();
    };
  });
