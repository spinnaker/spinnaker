'use strict';

import * as angular from 'angular';
import _ from 'lodash';

import {
  AccountService,
  CACHE_INITIALIZER_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  SECURITY_GROUP_READER,
} from '@spinnaker/core';
import { AZURE_IMAGE_IMAGE_READER } from '../../image/image.reader';
import { AZURE_INSTANCE_AZUREINSTANCETYPE_SERVICE } from '../../instance/azureInstanceType.service';

export const AZURE_SERVERGROUP_CONFIGURE_SERVERGROUPCONFIGURATION_SERVICE =
  'spinnaker.azure.serverGroup.configure.service';
export const name = AZURE_SERVERGROUP_CONFIGURE_SERVERGROUPCONFIGURATION_SERVICE; // for backwards compatibility
angular
  .module(AZURE_SERVERGROUP_CONFIGURE_SERVERGROUPCONFIGURATION_SERVICE, [
    AZURE_IMAGE_IMAGE_READER,
    LOAD_BALANCER_READ_SERVICE,
    SECURITY_GROUP_READER,
    CACHE_INITIALIZER_SERVICE,
    AZURE_INSTANCE_AZUREINSTANCETYPE_SERVICE,
  ])
  .factory('azureServerGroupConfigurationService', [
    '$q',
    'azureImageReader',
    'securityGroupReader',
    'cacheInitializer',
    'loadBalancerReader',
    'azureInstanceTypeService',
    function(
      $q,
      azureImageReader,
      securityGroupReader,
      cacheInitializer,
      loadBalancerReader,
      azureInstanceTypeService,
    ) {
      let dataDiskTypes = ['Standard_LRS', 'StandardSSD_LRS', 'Premium_LRS'];
      let dataDiskCachingTypes = ['None', 'ReadOnly', 'ReadWrite'];

      let healthCheckTypes = ['EC2', 'ELB'];
      let terminationPolicies = [
        'OldestInstance',
        'NewestInstance',
        'OldestLaunchConfiguration',
        'ClosestToNextInstanceHour',
        'Default',
      ];

      function configureUpdateCommand(command) {
        command.backingData = {
          healthCheckTypes: angular.copy(healthCheckTypes),
          terminationPolicies: angular.copy(terminationPolicies),
        };
      }

      function configureCommand(application, command) {
        return $q
          .all({
            credentialsKeyedByAccount: AccountService.getCredentialsKeyedByAccount('azure'),
            securityGroups: securityGroupReader.loadSecurityGroups(),
            loadBalancers: loadBalancerReader.loadLoadBalancers(application.name),
            dataDiskTypes: $q.when(angular.copy(dataDiskTypes)),
            dataDiskCachingTypes: $q.when(angular.copy(dataDiskCachingTypes)),
          })
          .then(function(backingData) {
            backingData.accounts = _.keys(backingData.credentialsKeyedByAccount);
            backingData.filtered = {};
            command.backingData = backingData;
            attachEventHandlers(command);
          });
      }

      function configureInstanceTypes(command) {
        const result = {
          dirty: {},
        };
        if (command.region) {
          const results = [result.dirty];

          // results.push(configureCustomInstanceTypes(command).dirty);
          results.push(configureStandardInstanceTypes(command).dirty);

          angular.extend(...results);
        } else {
          command.backingData.filtered.instanceTypes = [];
        }
        return result;
      }

      function configureStandardInstanceTypes(command) {
        const c = command;
        const result = {
          dirty: {},
        };

        const locations = [c.region];
        const { credentialsKeyedByAccount } = c.backingData;
        const { locationToInstanceTypesMap } = credentialsKeyedByAccount[c.credentials];

        if (locations.every(l => !l)) {
          return result;
        }

        let filtered = azureInstanceTypeService
          .getAvailableTypesForRegions(locationToInstanceTypesMap, locations)
          .map(type => type.name);

        const instanceType = c.instanceType;
        if (_.every([instanceType, !_.startsWith(instanceType, 'custom'), !_.includes(filtered, instanceType)])) {
          result.dirty.instanceType = c.instanceType;
          c.instanceType = null;
        }
        c.backingData.filtered.instanceTypes = filtered;
        return result;
      }

      function configureImages(command) {
        let result = {
          dirty: {},
        };
        let regionalImages = null;
        if (command.viewState.disableImageSelection) {
          return result;
        }
        if (command.region) {
          regionalImages = command.backingData.packageImages
            .filter(function(image) {
              return image.amis && image.amis[command.region];
            })
            .map(function(image) {
              return {
                imageName: image.imageName,
                ami: image.amis ? image.amis[command.region][0] : null,
              };
            });
          if (
            command.amiName &&
            !regionalImages.some(function(image) {
              return image.imageName === command.amiName;
            })
          ) {
            result.dirty.amiName = true;
            command.amiName = null;
          }
        } else {
          command.amiName = null;
        }
        command.backingData.filtered.images = regionalImages;
        return result;
      }

      function configureZones(command) {
        const result = { dirty: {} };
        const filteredData = command.backingData.filtered;
        if (!command.region) {
          return result;
        }
        let { regionsSupportZones, availabilityZones } = command.backingData.credentialsKeyedByAccount[
          command.credentials
        ];
        regionsSupportZones = regionsSupportZones || [];
        availabilityZones = availabilityZones || [];
        filteredData.zones = regionsSupportZones.includes(command.region) ? availabilityZones : [];

        return result;
      }

      function getRegionalSecurityGroups(command) {
        let newSecurityGroups = command.backingData.securityGroups[command.credentials] || {
          azure: {},
        };
        return _.chain(newSecurityGroups[command.region])
          .sortBy('name')
          .value();
      }

      function configureSecurityGroupOptions(command) {
        let result = {
          dirty: {},
        };
        let currentOptions;
        if (command.backingData.filtered.securityGroups) {
          currentOptions = command.backingData.filtered.securityGroups;
        }
        let newRegionalSecurityGroups = getRegionalSecurityGroups(command);
        if (command.selectedSecurityGroup) {
          // one has not been previously selected. We are either configuring for the
          //first time or they changed regions or account
          command.selectedSecurityGroup = null;
          result.dirty.securityGroups = true;
        }
        if (currentOptions != newRegionalSecurityGroups) {
          command.backingData.filtered.securityGroups = newRegionalSecurityGroups;
          result.dirty.securityGroups = true;
        }

        if (command.backingData.filtered.securityGroups === []) {
          command.viewState.securityGroupsConfigured = false;
        } else {
          command.viewState.securityGroupsConfigured = true;
        }

        return result;
      }

      function refreshSecurityGroups(command, skipCommandReconfiguration) {
        return cacheInitializer.refreshCache('securityGroups').then(function() {
          return securityGroupReader.getAllSecurityGroups().then(function(securityGroups) {
            command.backingData.securityGroups = securityGroups;
            if (!skipCommandReconfiguration) {
              configureSecurityGroupOptions(command);
            }
          });
        });
      }

      function getLoadBalancerNames(loadBalancers) {
        return _.chain(loadBalancers)
          .map('name')
          .uniq()
          .value()
          .sort();
      }

      function configureLoadBalancerOptions(command) {
        let result = {
          dirty: {},
        };
        let current = command.loadBalancers;
        let newLoadBalancers = getLoadBalancerNames(command.backingData.loadBalancers);

        if (current && command.loadBalancers) {
          let matched = _.intersection(newLoadBalancers, command.loadBalancers);
          let removed = _.xor(matched, current);
          command.loadBalancers = matched;
          if (removed.length) {
            result.dirty.loadBalancers = removed;
          }
        }
        command.backingData.filtered.loadBalancers = newLoadBalancers;
        return result;
      }

      function refreshLoadBalancers(command, skipCommandReconfiguration) {
        return loadBalancerReader.listLoadBalancers('azure').then(function(loadBalancers) {
          command.backingData.loadBalancers = loadBalancers;
          if (!skipCommandReconfiguration) {
            configureLoadBalancerOptions(command);
          }
        });
      }

      function configureLoadBalancers(command) {
        let result = {
          dirty: {},
        };
        let temp = command.backingData.loadBalancers;
        let filterlist = _.filter(temp, function(lb) {
          return lb.account === command.credentials && lb.region === command.region;
        });

        command.loadBalancers = getLoadBalancerNames(filterlist);
        command.viewState.loadBalancersConfigured = true;

        return result;
      }

      function attachEventHandlers(cmd) {
        cmd.regionChanged = function regionChanged(command, isInit = false) {
          let result = {
            dirty: {},
          };
          if (command.region && command.credentials) {
            angular.extend(result.dirty, configureLoadBalancers(command).dirty);
            angular.extend(result.dirty, configureSecurityGroupOptions(command).dirty);
            angular.extend(result.dirty, configureInstanceTypes(command).dirty);
            angular.extend(result.dirty, configureZones(command).dirty);
          }
          // reset previous set values
          if (!isInit) {
            command.loadBalancerName = null;
            command.loadBalancerType = null;
            command.vnet = null;
            command.vnetResourceGroup = null;
            command.subnet = null;
            command.selectedSubnet = null;
            command.selectedVnet = null;
            command.selectedVnetSubnets = [];
            command.viewState.networkSettingsConfigured = false;
            command.selectedSecurityGroup = null;
            command.securityGroupName = null;
            command.zonesEnabled = false;
            command.zones = [];
          }

          return result;
        };

        cmd.credentialsChanged = function credentialsChanged(command, isInit) {
          let result = {
            dirty: {},
          };
          let backingData = command.backingData;
          if (command.credentials) {
            let regionsForAccount = backingData.credentialsKeyedByAccount[command.credentials] || {
              regions: [],
              defaultKeyPair: null,
            };
            backingData.filtered.regions = regionsForAccount.regions;
            if (
              !_.chain(backingData.filtered.regions)
                .some({
                  name: command.region,
                })
                .value()
            ) {
              command.region = null;
              result.dirty.region = true;
            } else {
              angular.extend(result.dirty, command.regionChanged(command, isInit).dirty);
            }
            if (command.region) {
              angular.extend(result.dirty, configureLoadBalancers(command).dirty);
            }
            angular.extend(result.dirty, configureInstanceTypes(command).dirty);
          } else {
            command.region = null;
          }
          return result;
        };
      }

      function refreshInstanceTypes(command) {
        return cacheInitializer.refreshCache('instanceTypes').then(function() {
          return azureInstanceTypeService.getAllTypesByRegion().then(function(instanceTypes) {
            command.backingData.instanceTypes = instanceTypes;
            configureInstanceTypes(command);
          });
        });
      }

      return {
        configureUpdateCommand: configureUpdateCommand,
        configureCommand: configureCommand,
        configureImages: configureImages,
        configureSecurityGroupOptions: configureSecurityGroupOptions,
        configureLoadBalancerOptions: configureLoadBalancerOptions,
        refreshLoadBalancers: refreshLoadBalancers,
        refreshSecurityGroups: refreshSecurityGroups,
        getRegionalSecurityGroups: getRegionalSecurityGroups,
        refreshInstanceTypes: refreshInstanceTypes,
        configureZones: configureZones,
      };
    },
  ]);
