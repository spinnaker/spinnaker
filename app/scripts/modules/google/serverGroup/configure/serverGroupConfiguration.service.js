'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.gce.configuration.service', [
  require('../../../core/account/account.service.js'),
  require('../../../core/securityGroup/securityGroup.read.service.js'),
  require('../../../core/cache/cacheInitializer.js'),
  require('../../../core/loadBalancer/loadBalancer.read.service.js'),
  require('../../../core/network/network.read.service.js'),
  require('../../../core/subnet/subnet.read.service.js'),
  require('../../image/image.reader.js'),
  require('../../instance/gceInstanceType.service.js'),
  require('./../../instance/custom/customInstanceBuilder.gce.service.js'),
  require('../../loadBalancer/elSevenUtils.service.js'),
  require('../../httpHealthCheck/httpHealthCheck.reader.js'),
])
  .factory('gceServerGroupConfigurationService', function(gceImageReader, accountService, securityGroupReader,
                                                          gceInstanceTypeService, cacheInitializer,
                                                          $q, loadBalancerReader, networkReader, subnetReader,
                                                          settings, _, gceCustomInstanceBuilderService, elSevenUtils,
                                                          gceHttpHealthCheckReader) {

    var persistentDiskTypes = [
      'pd-standard',
      'pd-ssd'
    ];
    var authScopes = [
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
      var imageLoader;
      if (command.viewState.disableImageSelection) {
        imageLoader = $q.when(null);
      } else {
        imageLoader = command.viewState.imageId ? loadImagesFromImageName(command) : loadImagesFromApplicationName(application, command.selectedProvider);
      }

      return $q.all({
        credentialsKeyedByAccount: accountService.getCredentialsKeyedByAccount('gce'),
        securityGroups: securityGroupReader.getAllSecurityGroups(),
        networks: networkReader.listNetworksByProvider('gce'),
        subnets: subnetReader.listSubnetsByProvider('gce'),
        loadBalancers: loadBalancerReader.listLoadBalancers('gce'),
        packageImages: imageLoader,
        instanceTypes: gceInstanceTypeService.getAllTypesByRegion(),
        persistentDiskTypes: $q.when(angular.copy(persistentDiskTypes)),
        authScopes: $q.when(angular.copy(authScopes)),
        httpHealthChecks: gceHttpHealthCheckReader.listHttpHealthChecks(),
      }).then(function(backingData) {
        var loadBalancerReloader = $q.when(null);
        var securityGroupReloader = $q.when(null);
        var networkReloader = $q.when(null);
        var httpHealthCheckReloader = $q.when(null);
        backingData.accounts = _.keys(backingData.credentialsKeyedByAccount);
        backingData.filtered = {};
        command.backingData = backingData;
        configureImages(command);

        if (command.loadBalancers && command.loadBalancers.length) {
          // Verify all load balancers are accounted for; otherwise, try refreshing load balancers cache.
          var loadBalancerNames = _.pluck(getLoadBalancers(command), 'name');
          if (_.intersection(loadBalancerNames, command.loadBalancers).length < command.loadBalancers.length) {
            loadBalancerReloader = refreshLoadBalancers(command, true);
          }
        }
        if (command.securityGroups && command.securityGroups.length) {
          // Verify all security groups are accounted for; otherwise, try refreshing security groups cache.
          var securityGroupIds = _.pluck(getSecurityGroups(command), 'id');
          if (_.intersection(command.securityGroups, securityGroupIds).length < command.securityGroups.length) {
            securityGroupReloader = refreshSecurityGroups(command, true);
          }
        }
        if (command.network) {
          // Verify network is accounted for; otherwise, try refreshing networks cache.
          var networkNames = getNetworkNames(command);
          if (networkNames.indexOf(command.network) === -1) {
            networkReloader = refreshNetworks(command);
          }
        }
        if (command.autoHealingPolicy) {
          command.enableAutoHealing = true;
        }
        if (_.has(command, 'autoHealingPolicy.healthCheck')) {
          // Verify http health check is accounted for; otherwise, try refreshing http health checks cache.
          var httpHealthChecks = getHttpHealthChecks(command);
          if (!_(httpHealthChecks).contains(command.autoHealingPolicy.healthCheck)) {
            httpHealthCheckReloader = refreshHttpHealthChecks(command, true);
          }
        }

        return $q.all([loadBalancerReloader, securityGroupReloader, networkReloader, httpHealthCheckReloader]).then(function() {
          attachEventHandlers(command);
        });
      });
    }

    function loadImagesFromApplicationName(application, provider) {
      return gceImageReader.findImages({
        provider: provider,
        q: application.name.replace(/_/g, '[_\\-]') + '*',
      });
    }

    function loadImagesFromImageName(command) {
      command.image = command.viewState.imageId;

      var packageBase = command.image.split('_')[0];
      var parts = packageBase.split('-');
      if (parts.length > 3) {
        packageBase = parts.slice(0, -3).join('-');
      }
      if (!packageBase || packageBase.length < 3) {
        return [command.image];
      }

      return gceImageReader.findImages({
        provider: command.selectedProvider,
        q: packageBase + '*',
      });
    }

    function configureInstanceTypes(command) {
      let result = { dirty : {} };
      if (command.region) {
        let results = [ result.dirty ];

        results.push(configureCustomInstanceTypes(command).dirty);
        results.push(configureStandardInstanceTypes(command).dirty);

        angular.extend(...results);
      } else {
        command.backingData.filtered.instanceTypes = [];
      }
      return result;
    }

    function configureStandardInstanceTypes(command) {
      let result = { dirty: {} };
      let locations = command.regional ? [ command.region ] : [ command.zone ];
      let filtered = gceInstanceTypeService.getAvailableTypesForLocations(command.backingData.instanceTypes, locations);
      if (locations.every(l => !l)) {
        return result;
      }

      filtered = sortInstanceTypes(filtered);
      let instanceType = command.instanceType;
      if (_.every([ instanceType, !_.startsWith(instanceType, 'custom'), !_.contains(filtered, instanceType) ])) {
        result.dirty.instanceType = command.instanceType;
        command.instanceType = null;
      }
      command.backingData.filtered.instanceTypes = filtered;
      return result;
    }

    function configureCustomInstanceTypes(command) {
      let result = { dirty: {} },
        vCpuCount = _.get(command, 'viewState.customInstance.vCpuCount'),
        memory = _.get(command, 'viewState.customInstance.memory');
      let { zone, regional, region } = command;
      let location = regional ? region : zone;
      if (!location) {
        return result;
      }

      if (zone || regional) {
        _.set(command,
          'backingData.customInstanceTypes.vCpuList',
          gceCustomInstanceBuilderService.generateValidVCpuListForLocation(location)
        );
      }

      // initializes vCpuCount so that memory selector will be populated.
      if (_.some([ !vCpuCount, !gceCustomInstanceBuilderService.vCpuCountForLocationIsValid(vCpuCount, location)] )) {
        vCpuCount = _.get(command, 'backingData.customInstanceTypes.vCpuList[0]');
        _.set(command, 'viewState.customInstance.vCpuCount', vCpuCount);
      }

      _.set(
        command,
        'backingData.customInstanceTypes.memoryList',
        gceCustomInstanceBuilderService.generateValidMemoryListForVCpuCount(vCpuCount)
      );

      if (_.every([ memory, vCpuCount, !gceCustomInstanceBuilderService.memoryIsValid(memory, vCpuCount) ])) {
        _.set(
          command,
          'viewState.customInstance.memory',
          undefined
        );
        result.dirty.instanceType = command.instanceType;
        command.instanceType = null;
      }

      return result;
    }

    // n1-standard-8 should come before n1-standard-16, so we must sort by the individual segments of the names.
    function sortInstanceTypes(instanceTypes) {
      var tokenizedInstanceTypes = _.map(instanceTypes, instanceType => {
        let tokens = instanceType.split('-');

        return {
          class: tokens[0],
          group: tokens[1],
          index: Number(tokens[2]) || 0
        };
      });

      let sortedTokenizedInstanceTypes = _.sortByAll(tokenizedInstanceTypes, ['class', 'group', 'index']);

      return _.map(sortedTokenizedInstanceTypes, sortedTokenizedInstanceType => {
        return sortedTokenizedInstanceType.class + '-' + sortedTokenizedInstanceType.group + (sortedTokenizedInstanceType.index ? '-' + sortedTokenizedInstanceType.index : '');
      });
    }

    function configureImages(command) {
      var result = { dirty: {} };
      if (command.viewState.disableImageSelection) {
        return result;
      }
      if (command.credentials !== command.viewState.lastImageAccount) {
        command.viewState.lastImageAccount = command.credentials;
        var filteredImages = extractFilteredImages(command);
        command.backingData.filtered.images = filteredImages;
        if (!_(filteredImages).find({imageName: command.image})) {
          command.image = null;
          result.dirty.imageName = true;
        }
      }
      return result;
    }

    function configureZones(command) {
      var result = { dirty: {} };
      var filteredData = command.backingData.filtered;
      if (command.region === null) {
        return result;
      }
      let regions = command.backingData.credentialsKeyedByAccount[command.credentials].regions;
      if (_.isArray(regions)) {
        filteredData.zones = _.find(regions, {name: command.region}).zones;
        filteredData.truncatedZones = _.takeRight(filteredData.zones.sort(), 3);
      } else {
        // TODO(duftler): Remove this once we finish deprecating the old style regions/zones in clouddriver GCE credentials.
        filteredData.zones = regions[command.region];
      }
      if (!_(filteredData.zones).contains(command.zone)) {
        delete command.zone;
        if (!command.regional) {
          result.dirty.zone = true;
        }
      }
      return result;
    }

    function getHttpHealthChecks(command) {
      return _(command.backingData.httpHealthChecks[0].results)
        .filter({provider: 'gce', account: command.credentials})
        .pluck('name')
        .valueOf();
    }

    function configureHttpHealthChecks(command) {
      var result = { dirty: {} };
      var filteredData = command.backingData.filtered;

      if (command.credentials === null) {
        return result;
      }

      filteredData.httpHealthChecks = getHttpHealthChecks(command);

      if (_.has(command, 'autoHealingPolicy.healthCheck') && !_(filteredData.httpHealthChecks).contains(command.autoHealingPolicy.healthCheck)) {
        delete command.autoHealingPolicy.healthCheck;
        result.dirty.autoHealingPolicy = true;
      } else {
        result.dirty.autoHealingPolicy = null;
      }

      return result;
    }

    function getLoadBalancers(command) {
      return _(command.backingData.loadBalancers)
        .pluck('accounts')
        .flatten(true)
        .filter({name: command.credentials})
        .pluck('regions')
        .flatten(true)
        .pluck('loadBalancers')
        .flatten(true)
        .filter(_.curry(isRelevantLoadBalancer)(command))
        .unique()
        .valueOf();
    }

    function isRelevantLoadBalancer(command, loadBalancer) {
      return elSevenUtils.isElSeven(loadBalancer) || loadBalancer.region === command.region;
    }

    function configureLoadBalancerOptions(command) {
      var results = { dirty: {} };
      var current = command.loadBalancers;
      var newLoadBalancerObjects = getLoadBalancers(command);
      command.backingData.filtered.loadBalancerIndex = _.indexBy(newLoadBalancerObjects, 'name');
      command.backingData.filtered.loadBalancers = _.map(newLoadBalancerObjects, 'name');

      if (current && command.loadBalancers) {
        var matched = _.intersection(command.backingData.filtered.loadBalancers, command.loadBalancers);
        var removed = _.xor(matched, current);
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

      let backendsFromMetadata = command.backendServiceMetadata;
      let lbIndex = command.backingData.filtered.loadBalancerIndex;

      let backendServices = command.loadBalancers.reduce((backendServices, lbName) => {
        if (elSevenUtils.isElSeven(lbIndex[lbName])) {
          backendServices[lbName] = _.intersection(lbIndex[lbName].backendServices, backendsFromMetadata);
        }
        return backendServices;
      }, {});

      if (Object.keys(backendServices).length > 0) {
        command.backendServices = backendServices;
      }

    }

    function extractFilteredImages(command) {
      return _(command.backingData.packageImages)
        .filter({account: command.credentials})
        .unique()
        .valueOf();
    }

    function refreshLoadBalancers(command, skipCommandReconfiguration) {
      return cacheInitializer.refreshCache('loadBalancers').then(function() {
        return loadBalancerReader.listLoadBalancers('gce').then(function(loadBalancers) {
          command.backingData.loadBalancers = loadBalancers;
          if (!skipCommandReconfiguration) {
            configureLoadBalancerOptions(command);
          }
        });
      });
    }

    function refreshHttpHealthChecks(command, skipCommandReconfiguration) {
      return cacheInitializer.refreshCache('httpHealthChecks')
        .then(function() {
          return gceHttpHealthCheckReader.listHttpHealthChecks();
        })
        .then(function(httpHealthChecks) {
          command.backingData.httpHealthChecks = httpHealthChecks;
          if (!skipCommandReconfiguration) {
            configureHttpHealthChecks(command);
          }
        });
    }

    function configureSubnets(command) {
      var result = { dirty: {} };
      var filteredData = command.backingData.filtered;
      if (command.region === null) {
        return result;
      }
      filteredData.subnets = _(command.backingData.subnets)
        .filter({ account: command.credentials, network: command.network, region: command.region })
        .pluck('name')
        .valueOf();

      if (!_(filteredData.subnets).contains(command.subnet)) {
        command.subnet = '';
        result.dirty.subnet = true;
      }
      return result;
    }

    function getSecurityGroups(command) {
      var newSecurityGroups = command.backingData.securityGroups[command.credentials] || { gce: {}};
      newSecurityGroups = _.filter(newSecurityGroups.gce.global, function(securityGroup) {
        return securityGroup.network === command.network;
      });
      return _(newSecurityGroups)
        .sortBy('name')
        .valueOf();
    }

    function configureSecurityGroupOptions(command) {
      var results = { dirty: {} };
      var currentOptions = command.backingData.filtered.securityGroups;
      var newSecurityGroups = getSecurityGroups(command);
      if (currentOptions && command.securityGroups) {
        // not initializing - we are actually changing groups
        var currentGroupNames = command.securityGroups.map(function(groupId) {
          var match = _(currentOptions).find({id: groupId});
          return match ? match.name : groupId;
        });

        var matchedGroups = command.securityGroups.map(function(groupId) {
          var securityGroup = _(currentOptions).find({id: groupId}) ||
              _(currentOptions).find({name: groupId});
          return securityGroup ? securityGroup.name : null;
        }).map(function(groupName) {
          return _(newSecurityGroups).find({name: groupName});
        }).filter(function(group) {
          return group;
        });

        var matchedGroupNames = _.pluck(matchedGroups, 'name');
        var removed = _.xor(currentGroupNames, matchedGroupNames);
        command.securityGroups = _.pluck(matchedGroups, 'id');
        if (removed.length) {
          results.dirty.securityGroups = removed;
        }
      }

      // Only include explicit security group options in the pulldown list.
      command.backingData.filtered.securityGroups = _.filter(newSecurityGroups, function(securityGroup) {
        return !_.isEmpty(securityGroup.targetTags);
      });

      // Identify implicit security groups so they can be optionally listed in a read-only state.
      command.implicitSecurityGroups = _.filter(newSecurityGroups, function(securityGroup) {
        return _.isEmpty(securityGroup.targetTags);
      });

      // Only include explicitly-selected security groups in the body of the command.
      command.securityGroups = _.difference(command.securityGroups, _.pluck(command.implicitSecurityGroups, 'id'));

      return results;
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

    function getNetworkNames(command) {
      return _.pluck(_.filter(command.backingData.networks, { account: command.credentials }), 'name');
    }

    function refreshNetworks(command) {
      networkReader.listNetworksByProvider('gce').then(function(gceNetworks) {
        command.backingData.networks = gceNetworks;
      });
    }

    function refreshInstanceTypes(command) {
      return cacheInitializer.refreshCache('instanceTypes').then(function() {
        return gceInstanceTypeService.getAllTypesByRegion().then(function(instanceTypes) {
          command.backingData.instanceTypes = instanceTypes;
          configureInstanceTypes(command);
        });
      });
    }

    function attachEventHandlers(command) {
      command.regionalChanged = function regionalChanged() {
        var result = { dirty: {} };
        var filteredData = command.backingData.filtered;
        var defaults = settings.providers.gce.defaults;
        if (command.regional) {
          command.zone = null;
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

      command.regionChanged = function regionChanged() {
        var result = { dirty: {} };
        var filteredData = command.backingData.filtered;
        angular.extend(result.dirty, configureSubnets(command).dirty);
        if (command.region) {
          angular.extend(result.dirty, configureInstanceTypes(command).dirty);
          angular.extend(result.dirty, configureZones(command).dirty);
          angular.extend(result.dirty, configureLoadBalancerOptions(command).dirty);
          angular.extend(result.dirty, configureImages(command).dirty);
        } else {
          filteredData.zones = null;
        }

        command.viewState.dirty = command.viewState.dirty || {};
        angular.extend(command.viewState.dirty, result.dirty);
        return result;
      };

      command.credentialsChanged = function credentialsChanged() {
        var result = { dirty: {} };
        var backingData = command.backingData;
        if (command.credentials) {
          let regions = backingData.credentialsKeyedByAccount[command.credentials].regions;
          if (_.isArray(regions)) {
            backingData.filtered.regions = _.map(regions, 'name');
          } else {
            // TODO(duftler): Remove this once we finish deprecating the old style regions/zones in clouddriver GCE credentials.
            backingData.filtered.regions = _.keys(regions);
          }
          if (backingData.filtered.regions.indexOf(command.region) === -1) {
            command.region = null;
            result.dirty.region = true;
          } else {
            angular.extend(result.dirty, command.regionChanged().dirty);
          }

          backingData.filtered.networks = getNetworkNames(command);
          if (backingData.filtered.networks.indexOf(command.network) === -1) {
            command.network = null;
            result.dirty.network = true;
          } else {
            angular.extend(result.dirty, command.networkChanged().dirty);
          }

          angular.extend(result.dirty, configureHttpHealthChecks(command).dirty);
        } else {
          command.region = null;
        }

        command.viewState.dirty = command.viewState.dirty || {};
        angular.extend(command.viewState.dirty, result.dirty);

        return result;
      };

      command.networkChanged = function networkChanged() {
        var result = { dirty: {} };

        command.viewState.autoCreateSubnets = _(command.backingData.networks)
          .filter({ account: command.credentials, name: command.network })
          .pluck('autoCreateSubnets')
          .head();

        command.viewState.subnets = _(command.backingData.networks)
          .filter({ account: command.credentials, name: command.network })
          .pluck('subnets')
          .head();

        angular.extend(result.dirty, configureSubnets(command).dirty);
        angular.extend(result.dirty, configureSecurityGroupOptions(command).dirty);

        command.viewState.dirty = command.viewState.dirty || {};
        angular.extend(command.viewState.dirty, result.dirty);

        return result;
      };

      command.zoneChanged = function zoneChanged() {
        var result = { dirty: { } };
        if (command.zone === undefined && !command.regional) {
          result.dirty.zone = true;
        }
        command.viewState.dirty = command.viewState.dirty || {};
        angular.extend(command.viewState.dirty, result.dirty);
        angular.extend(command.viewState.dirty, configureInstanceTypes(command).dirty);
        return result;
      };

      command.customInstanceChanged = function customInstanceChanged() {
        var result = { dirty : { } };

        command.viewState.dirty = command.viewState.dirty || {};
        angular.extend(result, command.viewState.dirty, configureCustomInstanceTypes(command).dirty);

        return result;
      };
    }

    return {
      configureCommand: configureCommand,
      configureInstanceTypes: configureInstanceTypes,
      configureImages: configureImages,
      configureZones: configureZones,
      configureSubnets: configureSubnets,
      configureLoadBalancerOptions: configureLoadBalancerOptions,
      refreshLoadBalancers: refreshLoadBalancers,
      refreshSecurityGroups: refreshSecurityGroups,
      refreshInstanceTypes: refreshInstanceTypes,
      refreshHttpHealthChecks: refreshHttpHealthChecks,
    };


  });
