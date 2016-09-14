'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.serverGroup.configure.clone', [
  require('angular-ui-router'),
  require('../../../../core/application/modal/platformHealthOverride.directive.js'),
  require('../../../../core/serverGroup/serverGroup.write.service.js'),
  require('../../../../core/modal/wizard/v2modalWizard.service.js'),
  require('../../../../core/task/monitor/taskMonitorService.js'),
  require('../serverGroupConfiguration.service.js'),
])
  .controller('openstackCloneServerGroupCtrl', function($scope, $uibModalInstance, _, $q, $state,
                                                               serverGroupWriter, v2modalWizardService, taskMonitorService,
                                                               openstackServerGroupConfigurationService,
                                                               serverGroupCommand, application, title) {
    $scope.pages = {
      templateSelection: require('./templateSelection.html'),
      basicSettings: require('./location/basicSettings.html'),
      instanceSettings: require('./instance/instanceSettings.html'),
      clusterSettings: require('./clusterSettings.html'),
      accessSettings: require('./access/accessSettings.html'),
      advancedSettings: require('./advanced/advancedSettings.html')
    };

    $scope.title = title;

    $scope.applicationName = application.name;
    $scope.application = application;

    $scope.command = serverGroupCommand;
    $scope.contextImages = serverGroupCommand.viewState.contextImages;

    $scope.state = {
      loaded: false,
      requiresTemplateSelection: !!serverGroupCommand.viewState.requiresTemplateSelection,
    };

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: 'Creating your server group',
      forceRefreshMessage: 'Getting your new server group from Openstack...',
      modalInstance: $uibModalInstance,
      forceRefreshEnabled: true
    });

    function configureCommand() {
      serverGroupCommand.viewState.contextImages = $scope.contextImages;
      $scope.contextImages = null;
      openstackServerGroupConfigurationService.configureCommand(application, serverGroupCommand).then(function () {
        $scope.state.loaded = true;
        initializeWizardState();
        initializeWatches();
      });
    }

    function initializeWatches() {
      $scope.$watch('command.account', $scope.command.accountChanged);
      $scope.$watch('command.namespace', $scope.command.namespaceChanged);
    }

    function initializeWizardState() {
      var mode = serverGroupCommand.viewState.mode;
      if (mode === 'clone' || mode === 'editPipeline') {
        v2modalWizardService.markComplete('location');
        v2modalWizardService.markComplete('access-settings');
      }
    }

    this.isValid = function () {
      return $scope.command &&
        $scope.command.account !== null &&
        v2modalWizardService.isComplete();
    };

    this.showSubmitButton = function () {
      return v2modalWizardService.allPagesVisited();
    };

    this.submit = function () {
      if ($scope.command.viewState.mode === 'editPipeline' || $scope.command.viewState.mode == 'createPipeline') {
        return $uibModalInstance.close($scope.command);
      }
      $scope.taskMonitor.submit(
        function() {
          return serverGroupWriter.cloneServerGroup(angular.copy($scope.command), application);
        }
      );
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
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
