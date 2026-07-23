import _ from 'lodash';

import { AccountService } from '@spinnaker/core';
import { AzureInstanceTypeService } from '../../instance/azureInstanceType.service';

export class AzureServerGroupConfigurationService {
  static requiresDeckRuntimeServices = true;

  constructor($q, runtimeServices) {
    this.$q = $q;
    this.azureInstanceTypeService = new AzureInstanceTypeService($q);
    this.cacheInitializer = runtimeServices.cacheInitializer;
    this.loadBalancerReader = runtimeServices.loadBalancerReader;
    this.securityGroupReader = runtimeServices.securityGroupReader;
  }

  createDelegate() {
    const all = this.$q && this.$q.all ? this.$q.all.bind(this.$q) : Promise.all.bind(Promise);
    const azureInstanceTypeService = this.azureInstanceTypeService;
    const cacheInitializer = this.cacheInitializer;
    const loadBalancerReader = this.loadBalancerReader;
    const securityGroupReader = this.securityGroupReader;
    const dataDiskTypes = ['Standard_LRS', 'StandardSSD_LRS', 'Premium_LRS'];
    const dataDiskCachingTypes = ['None', 'ReadOnly', 'ReadWrite'];

    const healthCheckTypes = ['EC2', 'ELB'];
    const terminationPolicies = [
      'OldestInstance',
      'NewestInstance',
      'OldestLaunchConfiguration',
      'ClosestToNextInstanceHour',
      'Default',
    ];

    function configureUpdateCommand(command) {
      command.backingData = {
        healthCheckTypes: _.cloneDeep(healthCheckTypes),
        terminationPolicies: _.cloneDeep(terminationPolicies),
      };
    }

    function configureCommand(application, command) {
      return all([
        AccountService.getCredentialsKeyedByAccount('azure'),
        securityGroupReader.loadSecurityGroups(),
        loadBalancerReader.loadLoadBalancers(application.name),
      ]).then(function ([credentialsKeyedByAccount, securityGroups, loadBalancers]) {
        command.backingData = {
          credentialsKeyedByAccount,
          securityGroups,
          loadBalancers,
          dataDiskTypes: _.cloneDeep(dataDiskTypes),
          dataDiskCachingTypes: _.cloneDeep(dataDiskCachingTypes),
          accounts: _.keys(credentialsKeyedByAccount),
          filtered: {},
        };
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

        _.extend(...results);
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

      if (locations.every((l) => !l)) {
        return result;
      }

      const filtered = azureInstanceTypeService
        .getAvailableTypesForRegions(locationToInstanceTypesMap, locations)
        .map((type) => type.name);

      const instanceType = c.instanceType;
      if (_.every([instanceType, !_.startsWith(instanceType, 'custom'), !_.includes(filtered, instanceType)])) {
        result.dirty.instanceType = c.instanceType;
        c.instanceType = null;
      }
      c.backingData.filtered.instanceTypes = filtered;
      return result;
    }

    function configureImages(command) {
      const result = {
        dirty: {},
      };
      let regionalImages = null;
      const clearSelectedImage = function () {
        if (command.amiName) {
          result.dirty.amiName = true;
          command.amiName = null;
        }
        if (command.imageName) {
          result.dirty.imageName = true;
          command.imageName = null;
        }
        if (command.selectedImage) {
          result.dirty.selectedImage = true;
          command.selectedImage = null;
        }
      };
      if (command.viewState.disableImageSelection) {
        return result;
      }
      if (command.region) {
        regionalImages = (command.backingData.packageImages || command.images || [])
          .filter(function (image) {
            return image.amis && image.amis[command.region];
          })
          .map(function (image) {
            return {
              imageName: image.imageName,
              ami: image.amis ? image.amis[command.region][0] : null,
            };
          });
        const selectedImageName =
          command.imageName || (command.selectedImage && command.selectedImage.imageName) || command.amiName;
        const selectedRegionalImage = regionalImages.find(function (image) {
          return image.imageName === selectedImageName;
        });
        if (selectedImageName && !selectedRegionalImage) {
          clearSelectedImage();
        } else if (selectedRegionalImage && !command.image?.isCustom) {
          command.imageName = selectedRegionalImage.imageName;
          command.selectedImage = selectedRegionalImage;
        }
      } else {
        clearSelectedImage();
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
      const newSecurityGroups = command.backingData.securityGroups[command.credentials] || {
        azure: {},
      };
      return _.chain(newSecurityGroups[command.region]).sortBy('name').value();
    }

    function configureSecurityGroupOptions(command) {
      const result = {
        dirty: {},
      };
      let currentOptions;
      if (command.backingData.filtered.securityGroups) {
        currentOptions = command.backingData.filtered.securityGroups;
      }
      const newRegionalSecurityGroups = getRegionalSecurityGroups(command);
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

      if (command.backingData.filtered.securityGroups.length === 0) {
        command.viewState.securityGroupsConfigured = false;
      } else {
        command.viewState.securityGroupsConfigured = true;
      }

      return result;
    }

    function refreshSecurityGroups(command, skipCommandReconfiguration) {
      return cacheInitializer.refreshCache('securityGroups').then(function () {
        return securityGroupReader.getAllSecurityGroups().then(function (securityGroups) {
          command.backingData.securityGroups = securityGroups;
          if (!skipCommandReconfiguration) {
            configureSecurityGroupOptions(command);
          }
        });
      });
    }

    function getLoadBalancerNames(loadBalancers) {
      return _.chain(loadBalancers).map('name').uniq().value().sort();
    }

    function configureLoadBalancerOptions(command) {
      const result = {
        dirty: {},
      };
      const current = command.loadBalancers;
      const newLoadBalancers = getLoadBalancerNames(command.backingData.loadBalancers);

      if (current && command.loadBalancers) {
        const matched = _.intersection(newLoadBalancers, command.loadBalancers);
        const removed = _.xor(matched, current);
        command.loadBalancers = matched;
        if (removed.length) {
          result.dirty.loadBalancers = removed;
        }
      }
      command.backingData.filtered.loadBalancers = newLoadBalancers;
      return result;
    }

    function refreshLoadBalancers(command, skipCommandReconfiguration) {
      return loadBalancerReader.listLoadBalancers('azure').then(function (loadBalancers) {
        command.backingData.loadBalancers = loadBalancers;
        if (!skipCommandReconfiguration) {
          configureLoadBalancerOptions(command);
        }
      });
    }

    function configureLoadBalancers(command) {
      const result = {
        dirty: {},
      };
      const temp = command.backingData.loadBalancers;
      const filterlist = _.filter(temp, function (lb) {
        return lb.account === command.credentials && lb.region === command.region;
      });

      command.loadBalancers = getLoadBalancerNames(filterlist);
      command.viewState.loadBalancersConfigured = true;

      return result;
    }

    function attachEventHandlers(cmd) {
      cmd.regionChanged = function regionChanged(command, isInit = false) {
        const result = {
          dirty: {},
        };
        if (command.region && command.credentials) {
          _.extend(result.dirty, configureLoadBalancers(command).dirty);
          _.extend(result.dirty, configureSecurityGroupOptions(command).dirty);
          _.extend(result.dirty, configureInstanceTypes(command).dirty);
          _.extend(result.dirty, configureZones(command).dirty);
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
        const result = {
          dirty: {},
        };
        const backingData = command.backingData;
        if (command.credentials) {
          const regionsForAccount = backingData.credentialsKeyedByAccount[command.credentials] || {
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
            _.extend(result.dirty, command.regionChanged(command, isInit).dirty);
          }
          if (command.region) {
            _.extend(result.dirty, configureLoadBalancers(command).dirty);
          }
          _.extend(result.dirty, configureInstanceTypes(command).dirty);
        } else {
          command.region = null;
        }
        return result;
      };
    }

    function refreshInstanceTypes(command) {
      return cacheInitializer.refreshCache('instanceTypes').then(function () {
        return azureInstanceTypeService.getAllTypesByRegion().then(function (instanceTypes) {
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
  }
}

[
  'configureUpdateCommand',
  'configureCommand',
  'configureImages',
  'configureSecurityGroupOptions',
  'configureLoadBalancerOptions',
  'refreshLoadBalancers',
  'refreshSecurityGroups',
  'getRegionalSecurityGroups',
  'refreshInstanceTypes',
  'configureZones',
].forEach((method) => {
  AzureServerGroupConfigurationService.prototype[method] = function (...args) {
    if (!this.delegate) {
      this.delegate = this.createDelegate();
    }
    return this.delegate[method](...args);
  };
});
