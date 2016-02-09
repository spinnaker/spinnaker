'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.kubernetes.clone', [
  require('angular-ui-router'),
  require('../../../../core/application/modal/platformHealthOverride.directive.js'),
  require('../../../../core/serverGroup/serverGroup.write.service.js'),
  require('../../../../core/modal/wizard/modalWizard.service.js'),
  require('../../../../core/task/monitor/taskMonitorService.js'),
  require('../configuration.service.js'),
])
  .controller('kubernetesCloneServerGroupController', function($scope, $modalInstance, _, $q, $state,
                                                         serverGroupWriter, modalWizardService, taskMonitorService,
                                                         kubernetesServerGroupConfigurationService,
                                                         serverGroupCommand, application, title) {
    $scope.pages = {
      basicSettings: require('./basicSettings.html'),
      loadBalancers: require('./loadBalancers.html'),
      securityGroups: require('./securityGroups.html'),
      containers: require('./containers.html'),
      capacity: require('./capacity.html'),
    };

    $scope.title = title;

    $scope.applicationName = application.name;
    $scope.application = application;

    $scope.command = serverGroupCommand;

    $scope.state = {
      loaded: false,
      requiresTemplateSelection: !!serverGroupCommand.viewState.requiresTemplateSelection,
    };

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: 'Creating your server group',
      forceRefreshMessage: 'Getting your new server group from Kubernetes...',
      modalInstance: $modalInstance,
      forceRefreshEnabled: true
    });

    function configureCommand() {
      kubernetesServerGroupConfigurationService.configureCommand(application, serverGroupCommand).then(function () {
        var mode = serverGroupCommand.viewState.mode;
        if (mode === 'clone' || mode === 'create') {
          serverGroupCommand.viewState.useAllImageSelection = true;
        }
        $scope.state.loaded = true;
        initializeWizardState();
      });
    }

    function initializeWizardState() {
      var mode = serverGroupCommand.viewState.mode;
      if (mode === 'clone' || mode === 'editPipeline') {
        modalWizardService.getWizard().markComplete('location');
        modalWizardService.getWizard().markComplete('load-balancers');
        modalWizardService.getWizard().markComplete('security-groups');
        modalWizardService.getWizard().markComplete('containers');
        modalWizardService.getWizard().markComplete('capacity');
      }
    }

    this.isValid = function () {
      return $scope.command && $scope.command.containers.length > 0 &&
        $scope.command.credentials !== null &&
        modalWizardService.getWizard().isComplete();
    };

    this.showSubmitButton = function () {
      return modalWizardService.getWizard().allPagesVisited();
    };

    this.clone = function () {
      $scope.taskMonitor.submit(
        function() {
          return serverGroupWriter.cloneServerGroup(angular.copy($scope.command), application);
        }
      );
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };

    if (!$scope.state.requiresTemplateSelection) {
      configureCommand();
    } else {
      $scope.state.loaded = true;
    }

    $scope.$on('template-selected', function() {
      $scope.state.requiresTemplateSelection = false;
      configureCommand();
    });
  });
