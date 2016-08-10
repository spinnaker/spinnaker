'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.gce.cloneServerGroup', [
  require('angular-ui-router'),
  require('../../../../core/application/modal/platformHealthOverride.directive.js'),
  require('./../../../instance/custom/customInstanceBuilder.gce.service.js'),
  require('../../../../core/instance/instanceTypeService.js'),
  require('../../../../core/modal/wizard/wizardSubFormValidation.service.js'),
  require('./hiddenMetadataKeys.value.js'),
])
  .controller('gceCloneServerGroupCtrl', function($scope, $uibModalInstance, _, $q, $state,
                                                  serverGroupWriter, v2modalWizardService, taskMonitorService,
                                                  gceServerGroupConfigurationService,
                                                  serverGroupCommand, application, title,
                                                  gceCustomInstanceBuilderService, instanceTypeService,
                                                  wizardSubFormValidation, gceServerGroupHiddenMetadataKeys) {
    $scope.pages = {
      templateSelection: require('./templateSelection/templateSelection.html'),
      basicSettings: require('./location/basicSettings.html'),
      loadBalancers: require('./loadBalancers/loadBalancers.html'),
      securityGroups: require('./securityGroups/securityGroups.html'),
      instanceType: require('./instanceType/instanceType.html'),
      capacity: require('./capacity/capacity.html'),
      zones: require('./capacity/zones.html'),
      advancedSettings: require('./advancedSettings/advancedSettings.html'),
    };

    $scope.title = title;

    $scope.applicationName = application.name;
    $scope.application = application;

    $scope.command = serverGroupCommand;

    $scope.state = {
      loaded: false,
      requiresTemplateSelection: !!serverGroupCommand.viewState.requiresTemplateSelection,
    };

    function onApplicationRefresh() {
      // If the user has already closed the modal, do not navigate to the new details view
      if ($scope.$$destroyed) {
        return;
      }
      let [cloneStage] = $scope.taskMonitor.task.execution.stages.filter((stage) => stage.type === 'cloneServerGroup');
      if (cloneStage && cloneStage.context['deploy.server.groups']) {
        let newServerGroupName = cloneStage.context['deploy.server.groups'][$scope.command.region];
        if (newServerGroupName) {
          var newStateParams = {
            serverGroup: newServerGroupName,
            accountId: $scope.command.credentials,
            region: $scope.command.region,
            provider: 'gce',
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
      }
    }

    function onTaskComplete() {
      application.serverGroups.refresh();
      application.serverGroups.onNextRefresh($scope, onApplicationRefresh);
    }

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: 'Creating your server group',
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

    function configureCommand() {
      gceServerGroupConfigurationService.configureCommand(application, serverGroupCommand).then(function () {
        var mode = serverGroupCommand.viewState.mode;
        if (mode === 'clone' || mode === 'create') {
          if (!serverGroupCommand.backingData.packageImages || !serverGroupCommand.backingData.packageImages.length) {
            serverGroupCommand.viewState.useAllImageSelection = true;
          }
        }
        $scope.state.loaded = true;
        initializeSelectOptions();
        initializeWatches();
        wizardSubFormValidation
          .config({ scope: $scope, form: 'form'})
          .register({ page: 'location', subForm: 'basicSettings' })
          .register({ page: 'capacity', subForm: 'capacitySubForm' })
          .register({ page: 'zones', subForm: 'zonesSubForm' })
          .register({ page: 'load-balancers', subForm: 'loadBalancerSubForm'});
      });
    }

    function initializeWatches() {
      $scope.$watch('command.credentials', createResultProcessor($scope.command.credentialsChanged));
      $scope.$watch('command.regional', createResultProcessor($scope.command.regionalChanged));
      $scope.$watch('command.region', createResultProcessor($scope.command.regionChanged));
      $scope.$watch('command.network', createResultProcessor($scope.command.networkChanged));
      $scope.$watch('command.zone', createResultProcessor($scope.command.zoneChanged));
      $scope.$watch('command.viewState.instanceTypeDetails', updateStorageSettingsFromInstanceType());
      $scope.$watch('command.viewState.customInstance', () => {
        $scope.command.customInstanceChanged();
        setInstanceTypeFromCustomChoices();
      }, true);
    }

    function initializeSelectOptions() {
      processCommandUpdateResult($scope.command.credentialsChanged());
      processCommandUpdateResult($scope.command.regionalChanged());
      processCommandUpdateResult($scope.command.regionChanged());
      processCommandUpdateResult($scope.command.networkChanged());
      processCommandUpdateResult($scope.command.zoneChanged());
      processCommandUpdateResult($scope.command.customInstanceChanged());
      gceServerGroupConfigurationService.configureSubnets($scope.command);
    }

    function createResultProcessor(method) {
      return function() {
        processCommandUpdateResult(method());
      };
    }

    function processCommandUpdateResult(result) {
      if (result.dirty.loadBalancers) {
        v2modalWizardService.markDirty('load-balancers');
      }
      if (result.dirty.securityGroups) {
        v2modalWizardService.markDirty('security-groups');
      }
      if (result.dirty.availabilityZones) {
        v2modalWizardService.markDirty('capacity');
      }
      if (result.dirty.instanceType) {
        v2modalWizardService.markDirty('instance-type');
      }
    }

    function setInstanceTypeFromCustomChoices() {
      let location = $scope.command.regional
        ? $scope.command.region
        : $scope.command.zone;

      let customInstanceChoices = [
          _.get($scope.command, 'viewState.customInstance.vCpuCount'),
          _.get($scope.command, 'viewState.customInstance.memory'),
          location
        ];

      if (_.every([ ...customInstanceChoices,
          gceCustomInstanceBuilderService.customInstanceChoicesAreValid(...customInstanceChoices)])) {
        $scope.command.instanceType = gceCustomInstanceBuilderService
          .generateInstanceTypeString(...customInstanceChoices);

        instanceTypeService
          .getInstanceTypeDetails($scope.command.selectedProvider, 'buildCustom')
          .then((instanceTypeDetails) => {
            $scope.command.viewState.instanceTypeDetails = instanceTypeDetails;
          });
      }
    }

    function updateStorageSettingsFromInstanceType() {
      return function(instanceTypeDetails) {
        if ($scope.command.viewState.initialized) {
          if (instanceTypeDetails && instanceTypeDetails.storage && instanceTypeDetails.storage.defaultSettings) {
            let defaultSettings = instanceTypeDetails.storage.defaultSettings;

            $scope.command.persistentDiskType = defaultSettings.persistentDiskType;
            $scope.command.persistentDiskSizeGb = defaultSettings.persistentDiskSizeGb;
            $scope.command.localSSDCount = defaultSettings.localSSDCount;

            delete $scope.command.viewState.overriddenStorageDescription;
          }
        } else {
          $scope.command.viewState.initialized = true;
        }
      };
    }

    this.isValid = function () {
      return $scope.command &&
        ($scope.command.viewState.disableImageSelection || $scope.command.image) &&
        ($scope.command.application) &&
        ($scope.command.credentials) && ($scope.command.instanceType) &&
        ($scope.command.region) && ($scope.command.regional || $scope.command.zone) &&
        ($scope.command.capacity.desired !== null) &&
        $scope.form.$valid &&
        v2modalWizardService.isComplete();
    };

    this.showSubmitButton = function () {
      return v2modalWizardService.allPagesVisited();
    };

    function generateDiskDescriptors() {
      let persistentDiskDescriptor = {
        type: $scope.command.persistentDiskType,
        sizeGb: $scope.command.persistentDiskSizeGb
      };
      let localSSDDiskDescriptor = {
        type: 'local-ssd',
        sizeGb: 375
      };

      $scope.command.disks = Array($scope.command.localSSDCount + 1);
      $scope.command.disks[0] = persistentDiskDescriptor;

      _.fill($scope.command.disks, localSSDDiskDescriptor, 1);
    }

    function buildLoadBalancerMetadata(loadBalancerNames, loadBalancerIndex, backendServices) {
      let metadata = {};

      if (_.get(loadBalancerNames, 'length') > 0) {
        metadata = loadBalancerNames.reduce((metadata, name) => {
          let loadBalancerDetails = loadBalancerIndex[name];

          if (loadBalancerDetails.loadBalancerType === 'HTTP') {
            metadata['global-load-balancer-names'].push(name);
          } else {
            metadata['load-balancer-names'].push(name);
          }
          return metadata;
        }, { 'load-balancer-names' : [], 'global-load-balancer-names': [] });
      }

      if (_.isObject(backendServices) && Object.keys(backendServices).length > 0) {
        metadata['backend-service-names'] = _.reduce(
          backendServices,
          (accumulatedBackends, backends) => accumulatedBackends.concat(backends),
          []);
      }

      for (let key in metadata) {
        if (metadata[key].length === 0) {
          delete metadata[key];
        } else {
          metadata[key] = _.uniq(metadata[key]).toString();
        }
      }

      return metadata;
    }

    this.submit = function () {
      generateDiskDescriptors();

      // We use this list of load balancer names when 'Enabling' a server group.
      var loadBalancerMetadata = buildLoadBalancerMetadata(
        $scope.command.loadBalancers,
        $scope.command.backingData.filtered.loadBalancerIndex,
        $scope.command.backendServices);

      angular.extend($scope.command.instanceMetadata, loadBalancerMetadata);

      var origTags = $scope.command.tags;
      var transformedTags = [];
      // The tags are stored using a 'value' attribute to enable the Add/Remove behavior in the wizard.
      $scope.command.tags.forEach(function(tag) {
        transformedTags.push(tag.value);
      });
      $scope.command.tags = transformedTags;

      $scope.command.targetSize = $scope.command.capacity.desired;

      // We want min/max set to the same value as desired.
      $scope.command.capacity.min = $scope.command.capacity.desired;
      $scope.command.capacity.max = $scope.command.capacity.desired;

      if ($scope.command.viewState.mode === 'editPipeline' || $scope.command.viewState.mode === 'createPipeline') {
        return $uibModalInstance.close($scope.command);
      }
      $scope.taskMonitor.submit(
        function() {
          var promise = serverGroupWriter.cloneServerGroup(angular.copy($scope.command), application);

          // Copy back the original objects so the wizard can still be used if the command needs to be resubmitted.
          $scope.command.instanceMetadata = _.omit($scope.command.instanceMetadata, gceServerGroupHiddenMetadataKeys);

          $scope.command.tags = origTags;

          return promise;
        }
      );
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };

    this.specialInstanceProfiles = new Set(['custom', 'buildCustom']);

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
