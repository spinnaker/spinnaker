'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.cloneServerGroup.controller', [
  require('angular-ui-router'),
  require('../../../../core/application/modal/platformHealthOverride.directive.js'),
  require('../../../../core/utils/lodash.js'),
  require('../serverGroupConfiguration.service.js'),
  require('../../../../core/serverGroup/serverGroup.write.service.js'),
  require('../../../../core/task/monitor/taskMonitorService.js'),
  require('../../../../core/modal/wizard/modalWizard.service.js'),
  require('../../../../core/overrideRegistry/override.registry.js'),
  require('../../../../core/serverGroup/configure/common/serverGroupCommand.registry.js'),
])
  .controller('awsCloneServerGroupCtrl', function($scope, $modalInstance, _, $q, $state,
                                                  serverGroupWriter, modalWizardService, taskMonitorService,
                                                  overrideRegistry, awsServerGroupConfigurationService,
                                                  serverGroupCommandRegistry,
                                                  serverGroupCommand, application, title) {
    $scope.pages = {
      templateSelection: overrideRegistry.getTemplate('aws.serverGroup.templateSelection', require('./templateSelection.html')),
      basicSettings: overrideRegistry.getTemplate('aws.serverGroup.basicSettings', require('./basicSettings.html')),
      loadBalancers: overrideRegistry.getTemplate('aws.serverGroup.loadBalancers', require('./loadBalancers.html')),
      securityGroups: overrideRegistry.getTemplate('aws.serverGroup.securityGroups', require('./securityGroups/securityGroups.html')),
      instanceArchetype: overrideRegistry.getTemplate('aws.serverGroup.instanceArchetype', require('./instanceArchetype.html')),
      instanceType: overrideRegistry.getTemplate('aws.serverGroup.instanceType', require('./instanceType.html')),
      capacity: overrideRegistry.getTemplate('aws.serverGroup.capacity', require('./capacity/capacity.html')),
      advancedSettings: overrideRegistry.getTemplate('aws.serverGroup.advancedSettings', require('./advancedSettings.html')),
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
      awsServerGroupConfigurationService.configureCommand(application, serverGroupCommand).then(function () {
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
      $scope.$watch('command.virtualizationType', createResultProcessor($scope.command.imageChanged));
      $scope.$watch('command.stack', $scope.command.clusterChanged);
      $scope.$watch('command.freeFormDetails', $scope.command.clusterChanged);

      // if any additional watches have been configured, add them
      serverGroupCommandRegistry.getCommandOverrides('aws').forEach((override) => {
        if (override.addWatches) {
          override.addWatches($scope.command).forEach((watchConfig) => {
            $scope.$watch(watchConfig.property, watchConfig.method);
          });
        }
      });
    }

    // TODO: Move to service
    function initializeSelectOptions() {
      processCommandUpdateResult($scope.command.credentialsChanged());
      processCommandUpdateResult($scope.command.regionChanged());
      awsServerGroupConfigurationService.configureSubnetPurposes($scope.command);
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
      if (result.dirty.instanceType) {
        if ($scope.command.viewState.instanceProfile === 'custom') {
          modalWizardService.getWizard().markDirty('instance-profile');
        } else {
          modalWizardService.getWizard().markDirty('instance-type');
        }
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
            provider: 'aws',
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
        ($scope.command.amiName !== null) &&
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
