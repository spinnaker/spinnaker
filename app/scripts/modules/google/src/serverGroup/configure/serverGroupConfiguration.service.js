'use strict';

import * as angular from 'angular';
import _ from 'lodash';

import {
  AccountService,
  CACHE_INITIALIZER_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  NetworkReader,
  SECURITY_GROUP_READER,
  SubnetReader,
} from '@spinnaker/core';

import { GCEProviderSettings } from '../../gce.settings';
import { GCE_HEALTH_CHECK_READER } from '../../healthCheck/healthCheck.read.service';
import { getHealthCheckOptions } from '../../healthCheck/healthCheckUtils';
import { GceImageReader } from '../../image';
import { GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCEBUILDER_GCE_SERVICE } from './../../instance/custom/customInstanceBuilder.gce.service';
import { GOOGLE_INSTANCE_GCEINSTANCETYPE_SERVICE } from '../../instance/gceInstanceType.service';
import { GCE_HTTP_LOAD_BALANCER_UTILS } from '../../loadBalancer/httpLoadBalancerUtils.service';
import { LOAD_BALANCER_SET_TRANSFORMER } from '../../loadBalancer/loadBalancer.setTransformer';
import { GceAcceleratorService } from './wizard/advancedSettings/gceAccelerator.service';
import { GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGMANAGER_SERVICE } from './wizard/securityGroups/tagManager.service';

export const GOOGLE_SERVERGROUP_CONFIGURE_SERVERGROUPCONFIGURATION_SERVICE =
  'spinnaker.serverGroup.configure.gce.configuration.service';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_SERVERGROUPCONFIGURATION_SERVICE; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_CONFIGURE_SERVERGROUPCONFIGURATION_SERVICE, [
    LOAD_BALANCER_SET_TRANSFORMER,
    SECURITY_GROUP_READER,
    CACHE_INITIALIZER_SERVICE,
    LOAD_BALANCER_READ_SERVICE,
    GOOGLE_INSTANCE_GCEINSTANCETYPE_SERVICE,
    GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCEBUILDER_GCE_SERVICE,
    GCE_HTTP_LOAD_BALANCER_UTILS,
    GCE_HEALTH_CHECK_READER,
    GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGMANAGER_SERVICE,
  ])
  .factory('gceServerGroupConfigurationService', [
    'securityGroupReader',
    'gceInstanceTypeService',
    'cacheInitializer',
    '$q',
    'loadBalancerReader',
    'gceCustomInstanceBuilderService',
    'gceHttpLoadBalancerUtils',
    'gceHealthCheckReader',
    'gceTagManager',
    'gceLoadBalancerSetTransformer',
    function (
      securityGroupReader,
      gceInstanceTypeService,
      cacheInitializer,
      $q,
      loadBalancerReader,
      gceCustomInstanceBuilderService,
      gceHttpLoadBalancerUtils,
      gceHealthCheckReader,
      gceTagManager,
      gceLoadBalancerSetTransformer,
    ) {
      const persistentDiskTypes = ['pd-standard', 'pd-ssd'];
      const authScopes = [
        'cloud-platform',
        'userinfo.email',
        'compute.readonly',
        'compute',
        'cloud.useraccounts.readonly',
        'cloud.useraccounts',
        'devstorage.read_only',
        'devstorage.write_only',
        'devstorage.full_control',
        'taskqueue',
        'bigquery',
        'sqlservice.admin',
        'datastore',
        'logging.write',
        'logging.read',
        'logging.admin',
        'monitoring.write',
        'monitoring.read',
        'monitoring',
        'bigtable.data.readonly',
        'bigtable.data',
        'bigtable.admin',
        'bigtable.admin.table',
      ];

      function configureCommand(application, command) {
        return $q
          .all([
            AccountService.getCredentialsKeyedByAccount('gce'),
            securityGroupReader.getAllSecurityGroups(),
            NetworkReader.listNetworksByProvider('gce'),
            SubnetReader.listSubnetsByProvider('gce'),
            loadBalancerReader.listLoadBalancers('gce'),
            loadAllImages(command.credentials),
            gceInstanceTypeService.getAllTypesByRegion(),
            $q.when(angular.copy(persistentDiskTypes)),
            $q.when(angular.copy(authScopes)),
            gceHealthCheckReader.listHealthChecks(),
            AccountService.listAccounts('gce'),
          ])
          .then(function ([
            credentialsKeyedByAccount,
            securityGroups,
            networks,
            subnets,
            loadBalancers,
            allImages,
            instanceTypes,
            persistentDiskTypes,
            authScopes,
            healthChecks,
            accounts,
          ]) {
            const backingData = {
              credentialsKeyedByAccount,
              securityGroups,
              networks,
              subnets,
              loadBalancers,
              allImages,
              instanceTypes,
              persistentDiskTypes,
              authScopes,
              healthChecks,
              accounts,
            };
            let securityGroupReloader = $q.when(null);
            let networkReloader = $q.when(null);
            let healthCheckReloader = $q.when(null);
            backingData.filtered = {};
            backingData.distributionPolicyTargetShapes = getDistributionPolicyTargetShapes();
            command.backingData = backingData;
            configureImages(command);

            if (command.securityGroups && command.securityGroups.length) {
              // Verify all firewalls are accounted for; otherwise, try refreshing firewalls cache.
              const securityGroupIds = _.map(getSecurityGroups(command), 'id');
              if (_.intersection(command.securityGroups, securityGroupIds).length < command.securityGroups.length) {
                securityGroupReloader = refreshSecurityGroups(command, true);
              }
            }
            if (command.network) {
              // Verify network is accounted for; otherwise, try refreshing networks cache.
              const networkNames = getNetworkNames(command);
              if (!networkNames.includes(command.network)) {
                networkReloader = refreshNetworks(command);
              }
            }
            if (command.autoHealingPolicy) {
              command.enableAutoHealing = true;
            }
            if (_.has(command, 'autoHealingPolicy.healthCheck')) {
              // Verify health check is accounted for; otherwise, try refreshing health checks cache.
              const healthChecks = getHealthChecks(command);
              if (!_.chain(healthChecks).map('selfLink').includes(command.autoHealingPolicy.healthCheckUrl).value()) {
                healthCheckReloader = refreshHealthChecks(command, true);
              }
            }

            return $q.all([securityGroupReloader, networkReloader, healthCheckReloader]).then(() => {
              gceTagManager.register(command);
              attachEventHandlers(command);
            });
          });
      }

      function getDistributionPolicyTargetShapes() {
        return ['ANY', 'EVEN'];
      }

      function configureDistributionPolicyTargetShape(command) {
        const accountDetails = command.backingData.credentialsKeyedByAccount[command.credentials];
        if (!command.distributionPolicy.targetShape) {
          command.distributionPolicy.targetShape = 'EVEN';
        }
      }

      function loadAllImages(account) {
        return GceImageReader.findImages({
          account: account,
          provider: 'gce',
          q: '*',
        });
      }

      function configureInstanceTypes(command) {
        const result = { dirty: {} };
        if (command.region) {
          const results = [result.dirty];

          results.push(configureCustomInstanceTypes(command).dirty);
          results.push(configureStandardInstanceTypes(command).dirty);

          angular.extend(...results);
        } else {
          command.backingData.filtered.instanceTypes = [];
        }
        return result;
      }

      function configureCpuPlatforms(command) {
        const result = { dirty: {} };
        const filteredData = command.backingData.filtered;
        const locationToCpuPlatformsMap =
          command.backingData.credentialsKeyedByAccount[command.credentials].locationToCpuPlatformsMap;

        filteredData.cpuPlatforms = ['(Automatic)'];

        const location = command.regional ? command.region : command.zone;

        if (_.has(locationToCpuPlatformsMap, location)) {
          filteredData.cpuPlatforms = _.concat(filteredData.cpuPlatforms, locationToCpuPlatformsMap[location]);
        }

        if (!_.includes(filteredData.cpuPlatforms, command.minCpuPlatform)) {
          delete command.minCpuPlatform;
          result.dirty.minCpuPlatform = true;
        }
        return result;
      }

      function configureStandardInstanceTypes(command) {
        const c = command;
        const result = { dirty: {} };

        const locations = c.regional ? [c.region] : [c.zone];
        const { credentialsKeyedByAccount } = c.backingData;
        const { locationToInstanceTypesMap } = credentialsKeyedByAccount[c.credentials];

        if (locations.every((l) => !l)) {
          return result;
        }

        let filtered = gceInstanceTypeService.getAvailableTypesForLocations(locationToInstanceTypesMap, locations);

        filtered = sortInstanceTypes(filtered);
        const instanceType = c.instanceType;
        if (_.every([instanceType, !_.includes(instanceType, 'custom-'), !_.includes(filtered, instanceType)])) {
          result.dirty.instanceType = c.instanceType;
          c.instanceType = null;
        }
        c.backingData.filtered.instanceTypes = filtered;
        return result;
      }

      function configureCustomInstanceTypes(command) {
        const c = command;
        const result = { dirty: {} };
        let vCpuCount = _.get(c, 'viewState.customInstance.vCpuCount');
        const instanceFamily = _.get(c, 'viewState.customInstance.instanceFamily');
        const memory = _.get(c, 'viewState.customInstance.memory');
        const { zone, regional, region } = c;
        const { locationToInstanceTypesMap } = c.backingData.credentialsKeyedByAccount[c.credentials];
        const location = regional ? region : zone;

        if (!location) {
          return result;
        }

        if (zone || regional) {
          _.set(
            c,
            'backingData.customInstanceTypes.vCpuList',
            gceCustomInstanceBuilderService.generateValidVCpuListForLocation(location, locationToInstanceTypesMap),
          );
        }

        // initializes vCpuCount so that memory selector will be populated.
        if (
          !vCpuCount ||
          !gceCustomInstanceBuilderService.vCpuCountForLocationIsValid(
            instanceFamily,
            vCpuCount,
            location,
            locationToInstanceTypesMap,
          )
        ) {
          vCpuCount = _.get(c, 'backingData.customInstanceTypes.vCpuList[0]');
          _.set(c, 'viewState.customInstance.vCpuCount', vCpuCount);
        }

        _.set(
          c,
          'backingData.customInstanceTypes.memoryList',
          gceCustomInstanceBuilderService.generateValidMemoryListForVCpuCount(instanceFamily, vCpuCount),
        );

        if (
          _.every([
            memory,
            vCpuCount,
            !gceCustomInstanceBuilderService.memoryIsValid(instanceFamily, memory, vCpuCount),
          ])
        ) {
          _.set(c, 'viewState.customInstance.memory', undefined);
          result.dirty.instanceType = c.instanceType;
          c.instanceType = null;
        }

        _.set(
          c,
          'backingData.customInstanceTypes.instanceFamilyList',
          gceCustomInstanceBuilderService.generateValidInstanceFamilyList(),
        );

        return result;
      }

      function configureAccelerators(command) {
        const result = { dirty: {} };
        const acceleratorTypes = GceAcceleratorService.getAvailableAccelerators(command);
        _.set(command, ['viewState', 'acceleratorTypes'], acceleratorTypes);
        result.dirty.acceleratorTypes = true;
        const chosenAccelerators = _.get(command, 'acceleratorConfigs', []);
        if (chosenAccelerators.length > 0) {
          command.acceleratorConfigs = chosenAccelerators.filter(
            (chosen) => !!acceleratorTypes.find((a) => a.name === chosen.acceleratorType),
          );
          if (command.acceleratorConfigs.length === 0) {
            delete command.acceleratorConfigs;
          }
        }
        return result;
      }

      // n1-standard-8 should come before n1-standard-16, so we must sort by the individual segments of the names.
      function sortInstanceTypes(instanceTypes) {
        const tokenizedInstanceTypes = _.map(instanceTypes, (instanceType) => {
          const tokens = instanceType.split('-');

          return {
            class: tokens[0],
            group: tokens[1],
            index: Number(tokens[2]) || 0,
          };
        });

        const sortedTokenizedInstanceTypes = _.sortBy(tokenizedInstanceTypes, ['class', 'group', 'index']);

        return _.map(sortedTokenizedInstanceTypes, (sortedTokenizedInstanceType) => {
          return (
            sortedTokenizedInstanceType.class +
            '-' +
            sortedTokenizedInstanceType.group +
            (sortedTokenizedInstanceType.index ? '-' + sortedTokenizedInstanceType.index : '')
          );
        });
      }

      function configureImages(command) {
        command.image = command.viewState.imageId;
        const result = { dirty: {} };
        if (command.credentials !== command.viewState.lastImageAccount) {
          command.viewState.lastImageAccount = command.credentials;
          if (!_.chain(command.backingData.allImages).find({ imageName: command.image }).value()) {
            command.image = null;
            result.dirty.imageName = true;
          }
        }
        return result;
      }

      function configureZones(command) {
        const result = { dirty: {} };
        const filteredData = command.backingData.filtered;
        if (command.region === null) {
          return result;
        }
        const regions = command.backingData.credentialsKeyedByAccount[command.credentials].regions;
        if (_.isArray(regions)) {
          filteredData.zones = _.find(regions, { name: command.region }).zones;
          filteredData.truncatedZones = _.takeRight(filteredData.zones.sort(), 3);
        } else {
          // TODO(duftler): Remove this once we finish deprecating the old style regions/zones in clouddriver GCE credentials.
          filteredData.zones = regions[command.region];
        }
        if (!_.chain(filteredData.zones).includes(command.zone).value()) {
          delete command.zone;
          if (!command.regional) {
            result.dirty.zone = true;
          }
        }
        return result;
      }

      function getHealthChecks(command) {
        const matchingHealthChecks = _.filter(command.backingData.healthChecks, { account: command.credentials });
        return getHealthCheckOptions(matchingHealthChecks);
      }

      function configureHealthChecks(command) {
        const result = { dirty: {} };
        const filteredData = command.backingData.filtered;

        if (command.credentials === null) {
          return result;
        }

        filteredData.healthChecks = getHealthChecks(command);

        if (
          _.has(command, 'autoHealingPolicy.healthCheck') &&
          !_.chain(filteredData.healthChecks).map('selfLink').includes(command.autoHealingPolicy.healthCheckUrl).value()
        ) {
          delete command.autoHealingPolicy.healthCheck;
          result.dirty.autoHealingPolicy = true;
        } else {
          result.dirty.autoHealingPolicy = null;
        }

        return result;
      }

      function getLoadBalancers(command) {
        return _.chain(command.backingData.loadBalancers)
          .map('accounts')
          .flattenDeep()
          .filter({ name: command.credentials })
          .map('regions')
          .flattenDeep()
          .map('loadBalancers')
          .flattenDeep()
          .filter(_.curry(isRelevantLoadBalancer)(command))
          .uniq()
          .value();
      }

      function isRelevantLoadBalancer(command, loadBalancer) {
        return loadBalancer.region === command.region || loadBalancer.region === 'global';
      }

      function configureLoadBalancerOptions(command) {
        const results = { dirty: {} };
        const current = command.loadBalancers;
        const newLoadBalancerObjects = gceLoadBalancerSetTransformer.normalizeLoadBalancerSet(
          getLoadBalancers(command),
        );
        command.backingData.filtered.loadBalancerIndex = _.keyBy(newLoadBalancerObjects, 'name');
        command.backingData.filtered.loadBalancers = _.map(newLoadBalancerObjects, 'name');

        if (current && command.loadBalancers) {
          command.loadBalancers = gceHttpLoadBalancerUtils.normalizeLoadBalancerNamesForAccount(
            command.loadBalancers,
            command.credentials,
            newLoadBalancerObjects,
          );
          const matched = _.intersection(command.backingData.filtered.loadBalancers, command.loadBalancers);
          const removed = _.xor(matched, command.loadBalancers);
          command.loadBalancers = matched;
          configureBackendServiceOptions(command);

          if (removed.length) {
            results.dirty.loadBalancers = removed;
          }
        }
        return results;
      }

      function configureBackendServiceOptions(command) {
        /*
        a server group has a list of backend services, but there's no mapping from l7 -> backend service
        for the server group. this will not populate the wizard perfectly,
        but it is the best we can do with the given data.
      */

        const backendsFromMetadata = command.backendServiceMetadata;
        const lbIndex = command.backingData.filtered.loadBalancerIndex;

        const backendServices = command.loadBalancers.reduce((backendServices, lbName) => {
          if (gceHttpLoadBalancerUtils.isHttpLoadBalancer(lbIndex[lbName])) {
            backendServices[lbName] = _.intersection(lbIndex[lbName].backendServices, backendsFromMetadata);
          }
          return backendServices;
        }, {});

        if (Object.keys(backendServices).length > 0) {
          command.backendServices = backendServices;
        }
      }

      function refreshLoadBalancers(command, skipCommandReconfiguration) {
        return loadBalancerReader.listLoadBalancers('gce').then(function (loadBalancers) {
          command.backingData.loadBalancers = loadBalancers;
          if (!skipCommandReconfiguration) {
            configureLoadBalancerOptions(command);
          }
        });
      }

      function refreshHealthChecks(command, skipCommandReconfiguration) {
        return cacheInitializer
          .refreshCache('healthChecks')
          .then(function () {
            return gceHealthCheckReader.listHealthChecks();
          })
          .then(function (healthChecks) {
            command.backingData.healthChecks = healthChecks;
            if (!skipCommandReconfiguration) {
              configureHealthChecks(command);
            }
          });
      }

      function configureSubnets(command) {
        const result = { dirty: {} };
        const filteredData = command.backingData.filtered;
        if (command.region === null) {
          return result;
        }
        filteredData.subnets = _.chain(command.backingData.subnets)
          .filter({ account: command.credentials, network: command.network, region: command.region })
          .map('id')
          .value();

        if (!_.chain(filteredData.subnets).includes(command.subnet).value()) {
          command.subnet = '';
          result.dirty.subnet = true;
        }
        return result;
      }

      function getSecurityGroups(command) {
        let newSecurityGroups = command.backingData.securityGroups[command.credentials] || { gce: {} };
        newSecurityGroups = _.filter(newSecurityGroups.gce.global, function (securityGroup) {
          return securityGroup.network === command.network;
        });
        return _.chain(newSecurityGroups).sortBy('name').value();
      }

      function getXpnHostProjectIfAny(network) {
        if (network && network.includes('/')) {
          return network.split('/')[0] + '/';
        } else {
          return '';
        }
      }

      function configureSecurityGroupOptions(command) {
        const results = { dirty: {} };
        const currentOptions = command.backingData.filtered.securityGroups;
        const newSecurityGroups = getSecurityGroups(command);
        if (currentOptions && command.securityGroups) {
          // not initializing - we are actually changing groups
          const currentGroupNames = command.securityGroups.map(function (groupId) {
            const match = _.chain(currentOptions).find({ id: groupId }).value();
            return match ? match.id : groupId;
          });
          const matchedGroups = command.securityGroups
            .map(function (groupId) {
              const securityGroup = _.chain(currentOptions).find({ id: groupId }).value();
              return securityGroup ? securityGroup.id : null;
            })
            .map(function (groupName) {
              return _.chain(newSecurityGroups).find({ id: groupName }).value();
            })
            .filter(function (group) {
              return group;
            });
          command.securityGroups = _.map(matchedGroups, 'id');
          const removed = _.xor(currentGroupNames, command.securityGroups);
          if (removed.length) {
            results.dirty.securityGroups = removed;
          }
        }

        // Only include explicit firewall options in the pulldown list.
        command.backingData.filtered.securityGroups = _.filter(newSecurityGroups, function (securityGroup) {
          return !_.isEmpty(securityGroup.targetTags);
        });

        // Identify implicit firewalls so they can be optionally listed in a read-only state.
        command.implicitSecurityGroups = _.filter(newSecurityGroups, function (securityGroup) {
          return _.isEmpty(securityGroup.targetTags);
        });

        // Only include explicitly-selected firewalls in the body of the command.
        const xpnHostProject = getXpnHostProjectIfAny(command.network);
        const decoratedSecurityGroups = _.map(command.securityGroups, (sg) =>
          !sg.startsWith(xpnHostProject) ? xpnHostProject + sg : sg,
        );
        command.securityGroups = _.difference(decoratedSecurityGroups, _.map(command.implicitSecurityGroups, 'id'));

        return results;
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

      function getNetworkNames(command) {
        return _.map(_.filter(command.backingData.networks, { account: command.credentials }), 'id');
      }

      function refreshNetworks(command) {
        NetworkReader.listNetworksByProvider('gce').then(function (gceNetworks) {
          command.backingData.networks = gceNetworks;
        });
      }

      function refreshInstanceTypes(command) {
        return cacheInitializer.refreshCache('instanceTypes').then(function () {
          return gceInstanceTypeService.getAllTypesByRegion().then(function (instanceTypes) {
            command.backingData.instanceTypes = instanceTypes;
            configureInstanceTypes(command);
          });
        });
      }

      function attachEventHandlers(cmd) {
        cmd.regionalChanged = function regionalChanged(command) {
          const result = { dirty: {} };
          const filteredData = command.backingData.filtered;
          const defaults = GCEProviderSettings.defaults;
          if (command.regional) {
            command.zone = null;
            configureDistributionPolicyTargetShape(command);
          } else if (!command.zone) {
            if (command.region === defaults.region) {
              command.zone = defaults.zone;
            } else {
              command.zone = filteredData.zones[0];
            }

            angular.extend(result.dirty, configureZones(command).dirty);
          }

          command.viewState.dirty = command.viewState.dirty || {};
          angular.extend(command.viewState.dirty, result.dirty);
          return result;
        };

        cmd.regionChanged = function regionChanged(command) {
          const result = { dirty: {} };
          const filteredData = command.backingData.filtered;
          angular.extend(result.dirty, configureSubnets(command).dirty);
          if (command.region) {
            angular.extend(result.dirty, configureInstanceTypes(command).dirty);
            angular.extend(result.dirty, configureZones(command).dirty);
            angular.extend(result.dirty, configureCpuPlatforms(command).dirty);
            // TODO: Internal Load Balancers also need to be filtered by network.
            angular.extend(result.dirty, configureLoadBalancerOptions(command).dirty);
            angular.extend(result.dirty, configureImages(command).dirty);
          } else {
            filteredData.zones = null;
          }

          command.viewState.dirty = command.viewState.dirty || {};
          angular.extend(command.viewState.dirty, result.dirty);
          return result;
        };

        cmd.credentialsChanged = function credentialsChanged(command) {
          const result = { dirty: {} };
          const backingData = command.backingData;
          if (command.credentials) {
            const regions = backingData.credentialsKeyedByAccount[command.credentials].regions;
            if (_.isArray(regions)) {
              backingData.filtered.regions = _.map(regions, 'name');
            } else {
              // TODO(duftler): Remove this once we finish deprecating the old style regions/zones in clouddriver GCE credentials.
              backingData.filtered.regions = _.keys(regions);
            }
            if (!backingData.filtered.regions.includes(command.region)) {
              command.region = null;
              result.dirty.region = true;
            } else {
              angular.extend(result.dirty, command.regionChanged(command).dirty);
            }

            backingData.filtered.networks = getNetworkNames(command);
            if (!backingData.filtered.networks.includes(command.network)) {
              command.network = null;
              result.dirty.network = true;
            } else {
              angular.extend(result.dirty, command.networkChanged(command).dirty);
            }

            angular.extend(result.dirty, configureHealthChecks(command).dirty);
            angular.extend(result.dirty, configureInstanceTypes(command).dirty);

            configureDistributionPolicyTargetShape(command);
          } else {
            command.region = null;
          }

          command.viewState.dirty = command.viewState.dirty || {};
          angular.extend(command.viewState.dirty, result.dirty);

          return result;
        };

        cmd.networkChanged = function networkChanged(command) {
          const result = { dirty: {} };

          command.viewState.autoCreateSubnets = _.chain(command.backingData.networks)
            .filter({ account: command.credentials, id: command.network })
            .map('autoCreateSubnets')
            .head()
            .value();

          command.viewState.subnets = _.chain(command.backingData.networks)
            .filter({ account: command.credentials, id: command.network })
            .map('subnets')
            .head()
            .value();

          angular.extend(result.dirty, configureSubnets(command).dirty);
          angular.extend(result.dirty, configureSecurityGroupOptions(command).dirty);

          // TODO: Internal Load Balancers also need to be filtered by network.

          command.viewState.dirty = command.viewState.dirty || {};
          angular.extend(command.viewState.dirty, result.dirty);

          return result;
        };

        cmd.zoneChanged = function zoneChanged(command) {
          const result = { dirty: {} };
          if (command.zone === undefined && !command.regional) {
            result.dirty.zone = true;
          }
          command.viewState.dirty = command.viewState.dirty || {};
          angular.extend(command.viewState.dirty, result.dirty);
          angular.extend(command.viewState.dirty, configureInstanceTypes(command).dirty);
          angular.extend(command.viewState.dirty, configureCpuPlatforms(command).dirty);
          angular.extend(command.viewState.dirty, configureAccelerators(command).dirty);
          return result;
        };

        cmd.selectZonesChanged = function selectZonesChanged(command) {
          const result = { dirty: {} };
          command.viewState.dirty = command.viewState.dirty || {};
          angular.extend(command.viewState.dirty, result.dirty);
          angular.extend(command.viewState.dirty, configureAccelerators(command).dirty);
          return result;
        };

        cmd.customInstanceChanged = function customInstanceChanged(command) {
          const result = { dirty: {} };

          command.viewState.dirty = command.viewState.dirty || {};
          angular.extend(result, command.viewState.dirty, configureCustomInstanceTypes(command).dirty);

          return result;
        };
      }

      return {
        configureCommand,
        configureInstanceTypes,
        configureImages,
        configureZones,
        configureSubnets,
        configureLoadBalancerOptions,
        refreshLoadBalancers,
        refreshSecurityGroups,
        refreshInstanceTypes,
        refreshHealthChecks,
      };
    },
  ]);
