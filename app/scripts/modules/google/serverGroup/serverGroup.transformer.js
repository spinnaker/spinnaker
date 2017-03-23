'use strict';

import {defaults, uniq} from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.serverGroup.transformer', [])
  .factory('gceServerGroupTransformer', function () {

    function normalizeServerGroup(serverGroup, application) {
      return application.getDataSource('loadBalancers').ready().then(() => {
        if (serverGroup.loadBalancers) {
          let normalizedServerGroupLoadBalancerNames = [];
          // At this point, the HTTP(S) load balancers have been normalized (listener names mapped to URL map names).
          // Our server groups' lists of load balancer names still need to make this mapping.
          serverGroup.loadBalancers.forEach(loadBalancerName => {
            let matchingUrlMap = application.getDataSource('loadBalancers').data.find(loadBalancer => {
              return serverGroup.account === loadBalancer.account &&
                loadBalancer.listeners &&
                loadBalancer.listeners.map(listener => listener.name).includes(loadBalancerName);
            });

            matchingUrlMap
              ? normalizedServerGroupLoadBalancerNames.push(matchingUrlMap.name)
              : normalizedServerGroupLoadBalancerNames.push(loadBalancerName);
          });
          serverGroup.loadBalancers = uniq(normalizedServerGroupLoadBalancerNames);
        }
        return serverGroup;
      });
    }

    function convertServerGroupCommandToDeployConfiguration(base) {
      var truncatedZones = base.backingData.filtered.truncatedZones;

      // use defaults to avoid copying the backingData, which is huge and expensive to copy over
      var command = defaults({backingData: [], viewState: []}, base);
      if (base.viewState.mode !== 'clone') {
        delete command.source;
      }
      // We took this approach to avoid a breaking change to existing pipelines.
      command.disableTraffic = !command.enableTraffic;
      command.cloudProvider = 'gce';
      command.availabilityZones = {};
      command.availabilityZones[command.region] = base.zone ? [base.zone] : truncatedZones;
      command.account = command.credentials;
      delete command.viewState;
      delete command.backingData;
      delete command.selectedProvider;
      delete command.implicitSecurityGroups;
      delete command.persistentDiskType;
      delete command.persistentDiskSizeGb;
      delete command.localSSDCount;
      delete command.enableTraffic;
      delete command.providerType;
      delete command.enableAutoHealing;

      return command;
    }

    return {
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration,
      normalizeServerGroup: normalizeServerGroup,
    };

  });
