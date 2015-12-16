'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.cloneServerGroup.controller', [
  require('angular-ui-router'),
  require('../../../../core/utils/lodash.js'),
  require('../serverGroupConfiguration.service.js'),
  require('../../../../core/serverGroup/serverGroup.write.service.js'),
  require('../../../../core/task/monitor/taskMonitorService.js'),
  require('../../../../core/modal/wizard/modalWizard.service.js'),
])
  .controller('azureCloneServerGroupCtrl', function($scope, $modalInstance, _, $q, $exceptionHandler, $state,
                                                  serverGroupWriter, modalWizardService, taskMonitorService,
                                                  azureServerGroupConfigurationService, serverGroupCommand, application, title) {
    $scope.pages = {
      templateSelection: require('./templateSelection.html'),
      basicSettings: require('./basicSettings.html'),
      loadBalancers: require('./loadBalancers.html'),
      securityGroups: require('./securityGroups.html'),
      instanceArchetype: require('./instanceArchetype.html'),
      instanceType: require('./instanceType.html'),
      capacity: require('./capacity.html'),
      advancedSettings: require('./advancedSettings.html'),
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
      forceRefreshMessage: 'Getting your new server group from Amazon...',
      modalInstance: $modalInstance,
      forceRefreshEnabled: true
    });

    function configureCommand() {
      azureServerGroupConfigurationService.configureCommand(application, serverGroupCommand).then(function () {
        var mode = serverGroupCommand.viewState.mode;
        if (mode === 'clone' || mode === 'create') {
          if (!serverGroupCommand.backingData.packageImages.length) {
            serverGroupCommand.viewState.useAllImageSelection = true;
          }
        }
        $scope.state.loaded = true;
        initializeCommand();
        initializeWizardState();
        initializeSelectOptions();
        initializeWatches();
      });
    }

    function initializeWizardState() {
      if (serverGroupCommand.viewState.instanceProfile && serverGroupCommand.viewState.instanceProfile !== 'custom') {
        modalWizardService.getWizard().includePage('instance-type');
        modalWizardService.getWizard().markComplete('instance-type');
      }
      var mode = serverGroupCommand.viewState.mode;
      if (mode === 'clone' || mode === 'editPipeline') {
        modalWizardService.getWizard().markComplete('location');
        modalWizardService.getWizard().markComplete('load-balancers');
        modalWizardService.getWizard().markComplete('security-groups');
        modalWizardService.getWizard().markComplete('instance-profile');
        modalWizardService.getWizard().markComplete('instance-type');
        modalWizardService.getWizard().markComplete('capacity');
        modalWizardService.getWizard().markComplete('advanced');
      }
    }

    function initializeWatches() {
      $scope.$watch('command.credentials', createResultProcessor($scope.command.credentialsChanged));
      $scope.$watch('command.region', createResultProcessor($scope.command.regionChanged));
      $scope.$watch('command.subnetType', createResultProcessor($scope.command.subnetChanged));
      $scope.$watch('command.viewState.usePreferredZones', createResultProcessor($scope.command.usePreferredZonesChanged));
      $scope.$watch('command.stack', $scope.command.clusterChanged);
      $scope.$watch('command.freeFormDetails', $scope.command.clusterChanged);
      $scope.$watch('command.viewState.securityGroupDiffs', function(newVal) {
        if (newVal && newVal.length) {
          modalWizardService.getWizard().markDirty('security-groups');
        }
      });
      $scope.$watch('command.securityGroups', $scope.command.configureSecurityGroupDiffs);
    }

    // TODO: Move to service
    function initializeSelectOptions() {
      processCommandUpdateResult($scope.command.credentialsChanged());
      processCommandUpdateResult($scope.command.regionChanged());
      azureServerGroupConfigurationService.configureSubnetPurposes($scope.command);
    }

    function createResultProcessor(method) {
      return function() {
        processCommandUpdateResult(method());
      };
    }

    function processCommandUpdateResult(result) {
      if (result.dirty.loadBalancers) {
        modalWizardService.getWizard().markDirty('load-balancers');
      }
      if (result.dirty.securityGroups) {
        modalWizardService.getWizard().markDirty('security-groups');
      }
      if (result.dirty.availabilityZones) {
        modalWizardService.getWizard().markDirty('capacity');
      }
    }

    // TODO: Move to service, or don't
    function initializeCommand() {
      if (serverGroupCommand.viewState.imageId) {
        var foundImage = $scope.command.backingData.packageImages.filter(function(image) {
          return image.amis[serverGroupCommand.region] && image.amis[serverGroupCommand.region].indexOf(serverGroupCommand.viewState.imageId) !== -1;
        });
        if (foundImage.length) {
          serverGroupCommand.amiName = foundImage[0].imageName;
        }
      }
    }

    $scope.taskMonitor.onApplicationRefresh = function handleApplicationRefreshComplete() {
      // If the user has already closed the modal, do not navigate to the new details view
      if ($scope.$$destroyed) {
        return;
      }
      $scope.taskMonitor.task.getCompletedKatoTask().then(function(katoTask) {
        if (katoTask.resultObjects && katoTask.resultObjects.length && katoTask.resultObjects[0].serverGroupNames) {
          var newStateParams = {
            serverGroup: katoTask.resultObjects[0].serverGroupNames[0].split(':')[1],
            accountId: $scope.command.credentials,
            region: $scope.command.region,
            provider: 'azure',
          };
          var transitionTo = '^.^.^.clusters.serverGroup';
          if ($state.includes('**.clusters.serverGroup')) {  // clone via details, all view
            transitionTo = '^.serverGroup';
          }
          if ($state.includes('**.clusters.cluster.serverGroup')) { // clone or create with details open
            transitionTo = '^.^.serverGroup';
          }
          if ($state.includes('**.clusters')) { // create new, no details open
            transitionTo = '.serverGroup';
          }
          $state.go(transitionTo, newStateParams);
        }
      });
    };

    this.isValid = function () {
      return $scope.command &&
        ($scope.command.viewState.useAllImageSelection ? $scope.command.viewState.allImageSelection !== null : $scope.command.amiName !== null) &&
        ($scope.command.application !== null) &&
        ($scope.command.credentials !== null) && ($scope.command.instanceType !== null) &&
        ($scope.command.region !== null) && ($scope.command.availabilityZones !== null) &&
        ($scope.command.capacity.min !== null) && ($scope.command.capacity.max !== null) &&
        ($scope.command.capacity.desired !== null) &&
        modalWizardService.getWizard().isComplete();
    };

    this.showSubmitButton = function () {
      return modalWizardService.getWizard().allPagesVisited();
    };

    this.submit = function () {
      if ($scope.command.viewState.mode === 'editPipeline' || $scope.command.viewState.mode === 'createPipeline') {
        return $modalInstance.close($scope.command);
      }
      $scope.taskMonitor.submit(
        function() {
          return serverGroupWriter.cloneServerGroup($scope.command, application);
        }
      );
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };

    this.toggleSuspendedProcess = function(process) {
      $scope.command.suspendedProcesses = $scope.command.suspendedProcesses || [];
      var processIndex = $scope.command.suspendedProcesses.indexOf(process);
      if (processIndex === -1) {
        $scope.command.suspendedProcesses.push(process);
      } else {
        $scope.command.suspendedProcesses.splice(processIndex, 1);
      }
    };

    this.processIsSuspended = function(process) {
      return $scope.command.suspendedProcesses.indexOf(process) !== -1;
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
