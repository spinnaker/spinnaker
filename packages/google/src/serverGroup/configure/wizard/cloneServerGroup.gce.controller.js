'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';
import _ from 'lodash';

import { FirewallLabels, INSTANCE_TYPE_SERVICE, ModalWizard, TaskMonitor } from '@spinnaker/core';

import { GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_HIDDENMETADATAKEYS_VALUE } from './hiddenMetadataKeys.value';
import { GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCEBUILDER_GCE_SERVICE } from '../../../instance/custom/customInstanceBuilder.gce.service';
import { GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGMANAGER_SERVICE } from './securityGroups/tagManager.service';

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_GCE_CONTROLLER =
  'spinnaker.serverGroup.configure.gce.cloneServerGroup';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_GCE_CONTROLLER; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_GCE_CONTROLLER, [
    UIROUTER_ANGULARJS,
    GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCEBUILDER_GCE_SERVICE,
    INSTANCE_TYPE_SERVICE,
    GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_HIDDENMETADATAKEYS_VALUE,
    GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGMANAGER_SERVICE,
  ])
  .controller('gceCloneServerGroupCtrl', [
    '$scope',
    '$uibModalInstance',
    '$q',
    '$state',
    '$log',
    'serverGroupWriter',
    'gceServerGroupConfigurationService',
    'serverGroupCommand',
    'application',
    'title',
    'gceCustomInstanceBuilderService',
    'instanceTypeService',
    'wizardSubFormValidation',
    'gceServerGroupHiddenMetadataKeys',
    'gceTagManager',
    function (
      $scope,
      $uibModalInstance,
      $q,
      $state,
      $log,
      serverGroupWriter,
      gceServerGroupConfigurationService,
      serverGroupCommand,
      application,
      title,
      gceCustomInstanceBuilderService,
      instanceTypeService,
      wizardSubFormValidation,
      gceServerGroupHiddenMetadataKeys,
      gceTagManager,
    ) {
      $scope.pages = {
        templateSelection: require('./templateSelection/templateSelection.html'),
        basicSettings: require('./location/basicSettings.html'),
        loadBalancers: require('./loadBalancers/loadBalancers.html'),
        securityGroups: require('./securityGroups/securityGroups.html'),
        instanceType: require('./instanceType/instanceType.html'),
        capacity: require('./capacity/capacity.html'),
        zones: require('./capacity/zones.html'),
        autoHealingPolicy: require('./autoHealingPolicy/autoHealingPolicy.html'),
        advancedSettings: require('./advancedSettings/advancedSettings.html'),
      };

      $scope.firewallsLabel = FirewallLabels.get('Firewalls');

      $scope.title = title;

      $scope.applicationName = application.name;
      $scope.application = application;

      $scope.command = serverGroupCommand;

      $scope.state = {
        loaded: false,
        requiresTemplateSelection: !!serverGroupCommand.viewState.requiresTemplateSelection,
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

      function onApplicationRefresh() {
        // If the user has already closed the modal, do not navigate to the new details view
        if ($scope.$$destroyed) {
          return;
        }
        const cloneStage = $scope.taskMonitor.task.execution.stages.find((stage) => stage.type === 'cloneServerGroup');
        if (cloneStage && cloneStage.context['deploy.server.groups']) {
          const newServerGroupName = cloneStage.context['deploy.server.groups'][$scope.command.region];
          if (newServerGroupName) {
            const newStateParams = {
              serverGroup: newServerGroupName,
              accountId: $scope.command.credentials,
              region: $scope.command.region,
              provider: 'gce',
            };
            let transitionTo = '^.^.^.clusters.serverGroup';
            if ($state.includes('**.clusters.serverGroup')) {
              // clone via details, all view
              transitionTo = '^.serverGroup';
            }
            if ($state.includes('**.clusters.cluster.serverGroup')) {
              // clone or create with details open
              transitionTo = '^.^.serverGroup';
            }
            if ($state.includes('**.clusters')) {
              // create new, no details open
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

      $scope.taskMonitor = new TaskMonitor({
        application: application,
        title: 'Creating your server group',
        modalInstance: $uibModalInstance,
        onTaskComplete: onTaskComplete,
      });

      function configureCommand() {
        gceServerGroupConfigurationService
          .configureCommand(application, serverGroupCommand)
          .then(function () {
            $scope.state.loaded = true;
            initializeSelectOptions();
            initializeWatches();
            wizardSubFormValidation
              .config({ scope: $scope, form: 'form' })
              .register({ page: 'location', subForm: 'basicSettings' })
              .register({ page: 'capacity', subForm: 'capacitySubForm' })
              .register({ page: 'zones', subForm: 'zonesSubForm' })
              .register({ page: 'load-balancers', subForm: 'loadBalancerSubForm' })
              .register({ page: 'autohealing-policy', subForm: 'autoHealingPolicySubForm' });
          })
          .catch((e) => {
            $log.error('Error generating server group command: ', e);
          });
      }

      function initializeWatches() {
        $scope.$watch('command.credentials', createResultProcessor($scope.command.credentialsChanged));
        $scope.$watch('command.regional', createResultProcessor($scope.command.regionalChanged));
        $scope.$watch('command.region', createResultProcessor($scope.command.regionChanged));
        $scope.$watch('command.network', createResultProcessor($scope.command.networkChanged));
        $scope.$watch('command.zone', createResultProcessor($scope.command.zoneChanged));
        $scope.$watch('command.viewState.instanceTypeDetails', updateStorageSettingsFromInstanceType());
        $scope.$watch('command.selectZones', createResultProcessor($scope.command.selectZonesChanged));
        $scope.$watch('command.distributionPolicy.zones', createResultProcessor($scope.command.selectZonesChanged));
        $scope.$watch(
          'command.viewState.customInstance',
          () => {
            $scope.command.customInstanceChanged($scope.command);
            setInstanceTypeFromCustomChoices();
          },
          true,
        );
      }

      function initializeSelectOptions() {
        processCommandUpdateResult($scope.command.credentialsChanged($scope.command));
        processCommandUpdateResult($scope.command.regionalChanged($scope.command));
        processCommandUpdateResult($scope.command.regionChanged($scope.command));
        processCommandUpdateResult($scope.command.networkChanged($scope.command));
        processCommandUpdateResult($scope.command.zoneChanged($scope.command));
        processCommandUpdateResult($scope.command.customInstanceChanged($scope.command));
        gceServerGroupConfigurationService.configureSubnets($scope.command);
      }

      function createResultProcessor(method) {
        return function () {
          processCommandUpdateResult(method($scope.command));
        };
      }

      function processCommandUpdateResult(result) {
        if (result.dirty.loadBalancers) {
          ModalWizard.markDirty('load-balancers');
        }
        if (result.dirty.securityGroups) {
          ModalWizard.markDirty('security-groups');
        }
        if (result.dirty.availabilityZones) {
          ModalWizard.markDirty('capacity');
        }
        if (result.dirty.instanceType) {
          ModalWizard.markDirty('instance-type');
        }
      }

      function setInstanceTypeFromCustomChoices() {
        const c = $scope.command;
        const location = c.regional ? c.region : c.zone;
        const { locationToInstanceTypesMap } = c.backingData.credentialsKeyedByAccount[c.credentials];

        const customInstanceChoices = [
          _.get(c, 'viewState.customInstance.instanceFamily'),
          _.get(c, 'viewState.customInstance.vCpuCount'),
          _.get(c, 'viewState.customInstance.memory'),
        ];

        if (
          _.every([
            ...customInstanceChoices,
            gceCustomInstanceBuilderService.customInstanceChoicesAreValid(
              ...customInstanceChoices,
              location,
              locationToInstanceTypesMap,
            ),
          ])
        ) {
          c.instanceType = gceCustomInstanceBuilderService.generateInstanceTypeString(...customInstanceChoices);

          instanceTypeService.getInstanceTypeDetails(c.selectedProvider, 'buildCustom').then((instanceTypeDetails) => {
            c.viewState.instanceTypeDetails = instanceTypeDetails;
          });
        }
      }

      function updateStorageSettingsFromInstanceType() {
        return function (instanceTypeDetails) {
          if ($scope.command.viewState.initialized) {
            if (instanceTypeDetails && instanceTypeDetails.storage && instanceTypeDetails.storage.defaultSettings) {
              $scope.command.disks = instanceTypeDetails.storage.defaultSettings.disks;
              delete $scope.command.viewState.overriddenStorageDescription;
            }
          } else {
            $scope.command.viewState.initialized = true;
          }
        };
      }

      this.isValid = function () {
        const selectedZones =
          $scope.command.selectZones && _.get($scope, 'command.distributionPolicy.zones.length') >= 1;
        return (
          $scope.command &&
          ($scope.command.viewState.disableImageSelection || $scope.command.image) &&
          $scope.command.application &&
          $scope.command.credentials &&
          $scope.command.instanceType &&
          $scope.command.region &&
          ($scope.command.regional || $scope.command.zone) &&
          $scope.command.capacity.desired !== null &&
          (!$scope.command.selectZones || selectedZones) &&
          $scope.form.$valid &&
          ModalWizard.isComplete()
        );
      };

      this.showSubmitButton = function () {
        return ModalWizard.allPagesVisited();
      };

      function buildLoadBalancerMetadata(loadBalancerNames, loadBalancerIndex, backendServices) {
        let metadata = {};

        if (_.get(loadBalancerNames, 'length') > 0) {
          metadata = loadBalancerNames.reduce(
            (metadata, name) => {
              const loadBalancerDetails = loadBalancerIndex[name];

              if (loadBalancerDetails.loadBalancerType === 'HTTP') {
                metadata['global-load-balancer-names'] = metadata['global-load-balancer-names'].concat(
                  loadBalancerDetails.listeners.map((listener) => listener.name),
                );
              } else if (loadBalancerDetails.loadBalancerType === 'INTERNAL_MANAGED') {
                metadata['load-balancer-names'] = metadata['load-balancer-names'].concat(
                  loadBalancerDetails.listeners.map((listener) => listener.name),
                );
              } else if (loadBalancerDetails.loadBalancerType === 'SSL') {
                metadata['global-load-balancer-names'].push(name);
              } else if (loadBalancerDetails.loadBalancerType === 'TCP') {
                metadata['global-load-balancer-names'].push(name);
              } else {
                metadata['load-balancer-names'].push(name);
              }
              return metadata;
            },
            { 'load-balancer-names': [], 'global-load-balancer-names': [] },
          );
        }

        if (_.isObject(backendServices) && Object.keys(backendServices).length > 0) {
          metadata['backend-service-names'] = _.reduce(
            backendServices,
            (accumulatedBackends, backends) => accumulatedBackends.concat(backends),
            [],
          );
        }

        for (const key in metadata) {
          if (metadata[key].length === 0) {
            delete metadata[key];
          } else {
            metadata[key] = _.uniq(metadata[key]).toString();
          }
        }

        return metadata;
      }

      function collectLoadBalancerNamesForCommand(loadBalancerIndex, loadBalancerMetadata) {
        let loadBalancerNames = [];
        if (loadBalancerMetadata['load-balancer-names']) {
          loadBalancerNames = loadBalancerNames.concat(loadBalancerMetadata['load-balancer-names'].split(','));
        }

        const selectedSslLoadBalancerNames = _.chain(loadBalancerIndex)
          .filter({ loadBalancerType: 'SSL' })
          .map('name')
          .intersection(
            loadBalancerMetadata['global-load-balancer-names']
              ? loadBalancerMetadata['global-load-balancer-names'].split(',')
              : [],
          )
          .value();

        const selectedTcpLoadBalancerNames = _.chain(loadBalancerIndex)
          .filter({ loadBalancerType: 'TCP' })
          .map('name')
          .intersection(
            loadBalancerMetadata['global-load-balancer-names']
              ? loadBalancerMetadata['global-load-balancer-names'].split(',')
              : [],
          )
          .value();

        return loadBalancerNames.concat(selectedSslLoadBalancerNames).concat(selectedTcpLoadBalancerNames);
      }

      this.submit = function () {
        // We use this list of load balancer names when 'Enabling' a server group.
        const loadBalancerMetadata = buildLoadBalancerMetadata(
          $scope.command.loadBalancers,
          $scope.command.backingData.filtered.loadBalancerIndex,
          $scope.command.backendServices,
        );

        const origLoadBalancers = $scope.command.loadBalancers;
        $scope.command.loadBalancers = collectLoadBalancerNamesForCommand(
          $scope.command.backingData.filtered.loadBalancerIndex,
          loadBalancerMetadata,
        );

        if ($scope.command.minCpuPlatform === '(Automatic)') {
          $scope.command.minCpuPlatform = '';
        }

        angular.extend($scope.command.instanceMetadata, loadBalancerMetadata);

        const origTags = $scope.command.tags;
        const transformedTags = [];
        // The tags are stored using a 'value' attribute to enable the Add/Remove behavior in the wizard.
        $scope.command.tags.forEach(function (tag) {
          transformedTags.push(tag.value);
        });
        $scope.command.tags = transformedTags;

        $scope.command.targetSize = $scope.command.capacity.desired;

        if ($scope.command.autoscalingPolicy) {
          $scope.command.capacity.min = $scope.command.autoscalingPolicy.minNumReplicas;
          $scope.command.capacity.max = $scope.command.autoscalingPolicy.maxNumReplicas;
        } else {
          $scope.command.capacity.min = $scope.command.capacity.desired;
          $scope.command.capacity.max = $scope.command.capacity.desired;
        }

        delete $scope.command.securityGroups;

        if ($scope.command.viewState.mode === 'editPipeline' || $scope.command.viewState.mode === 'createPipeline') {
          return $uibModalInstance.close($scope.command);
        }

        $scope.taskMonitor.submit(function () {
          const promise = serverGroupWriter.cloneServerGroup(angular.copy($scope.command), application);

          // Copy back the original objects so the wizard can still be used if the command needs to be resubmitted.
          $scope.command.instanceMetadata = _.omit($scope.command.instanceMetadata, gceServerGroupHiddenMetadataKeys);

          $scope.command.tags = origTags;
          $scope.command.loadBalancers = origLoadBalancers;
          $scope.command.securityGroups = gceTagManager.inferSecurityGroupIdsFromTags($scope.command.tags);

          return promise;
        });
      };

      this.onHealthCheckRefresh = function () {
        gceServerGroupConfigurationService.refreshHealthChecks($scope.command);
      };

      this.onEnableAutoHealingChange = function () {
        // Prevent empty auto-healing policies from being overwritten by those of their ancestors
        $scope.command.overwriteAncestorAutoHealingPolicy =
          $scope.command.viewState.mode === 'clone' &&
          $scope.command.autoHealingPolicy != null &&
          $scope.command.enableAutoHealing === false;
      };

      this.setAutoHealingPolicy = function (autoHealingPolicy) {
        $scope.command.autoHealingPolicy = autoHealingPolicy;
      };

      this.cancel = function () {
        $uibModalInstance.dismiss();
      };

      this.specialInstanceProfiles = new Set(['custom', 'buildCustom']);

      // This function is called from within React, and without $apply, Angular does not know when it has been called.
      $scope.command.setCustomInstanceViewState = (customInstanceChoices) => {
        $scope.$apply(() => ($scope.command.viewState.customInstance = customInstanceChoices));
      };

      if (!$scope.state.requiresTemplateSelection) {
        configureCommand();
      } else {
        $scope.state.loaded = true;
      }

      this.templateSelected = () => {
        $scope.state.requiresTemplateSelection = false;
        configureCommand();
      };

      $scope.$on('$destroy', gceTagManager.reset);
    },
  ]);
