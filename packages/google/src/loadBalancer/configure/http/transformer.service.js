'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { NameUtils } from '@spinnaker/core';

import { sessionAffinityModelToViewMap, sessionAffinityViewToModelMap } from '../common/sessionAffinityNameMaps';

export const GOOGLE_LOADBALANCER_CONFIGURE_HTTP_TRANSFORMER_SERVICE = 'spinnaker.gce.deck.httpLoadBalancer.transformer';
export const name = GOOGLE_LOADBALANCER_CONFIGURE_HTTP_TRANSFORMER_SERVICE; // for backwards compatibility
module(GOOGLE_LOADBALANCER_CONFIGURE_HTTP_TRANSFORMER_SERVICE, []).factory(
  'gceHttpLoadBalancerTransformer',
  function () {
    // SERIALIZE

    const keysToOmit = ['backendServices', 'healthChecks', 'listeners', 'stack', 'detail'];

    function serialize(originalCommand, originalLoadBalancer) {
      const command = _.cloneDeep(originalCommand);
      const { loadBalancer, backingData } = command;

      mapComponentNamesToObjects(loadBalancer, backingData);

      loadBalancer.hostRules = loadBalancer.hostRules.reduce((hostRules, hostRule) => {
        return hostRules.concat(
          hostRule.hostPatterns.map((hostPattern) => {
            return {
              hostPatterns: [hostPattern],
              pathMatcher: _.cloneDeep(hostRule.pathMatcher),
            };
          }),
        );
      }, []);

      const commands = buildCommandForEachListener(loadBalancer);

      if (originalLoadBalancer) {
        commands[0].listenersToDelete = _.chain(originalLoadBalancer.listeners)
          .map('name')
          .difference(_.map(originalCommand.loadBalancer.listeners, 'name'))
          .value();
      }

      return commands;
    }

    function mapComponentNamesToObjects(loadBalancer, backingData) {
      const unifiedHealthChecksKeyedByName = _.assign(
        backingData.healthChecksKeyedByName,
        _.keyBy(loadBalancer.healthChecks, 'name'),
      );
      const unifiedBackendServicesKeyedByName = _.assign(
        backingData.backendServicesKeyedByName,
        _.keyBy(loadBalancer.backendServices, 'name'),
      );

      _.forEach(unifiedBackendServicesKeyedByName, (service) => {
        service.healthCheck = unifiedHealthChecksKeyedByName[service.healthCheck];
        // Map human readable text back to session affinity code.
        service.sessionAffinity = sessionAffinityViewToModelMap[service.sessionAffinity] || service.sessionAffinity;
      });

      loadBalancer.defaultService = unifiedBackendServicesKeyedByName[loadBalancer.defaultService];
      mapBackendServiceNamesToObjects(loadBalancer.hostRules, unifiedBackendServicesKeyedByName);
    }

    function mapBackendServiceNamesToObjects(hostRules, servicesByName) {
      hostRules.forEach((hostRule) => {
        const p = hostRule.pathMatcher;

        p.defaultService = servicesByName[p.defaultService];

        p.pathRules.forEach((pathRule) => {
          pathRule.backendService = servicesByName[pathRule.backendService];
        });
      });
    }

    function buildCommandForEachListener(loadBalancer) {
      return loadBalancer.listeners.map((listener) => {
        let command = _.cloneDeep(loadBalancer);
        command = _.omit(command, keysToOmit);
        command.name = listener.name;
        command.portRange = listener.port;
        command.certificate = listener.certificate || null;
        command.ipAddress = listener.ipAddress;
        command.subnet = listener.subnet;

        return command;
      });
    }

    // DESERIALIZE

    function deserialize(loadBalancer) {
      const { backendServices, healthChecks, defaultService } = getHealthChecksAndBackendServices(loadBalancer);
      const hostRules = getHostRules(loadBalancer);
      const listeners = getListeners(loadBalancer);

      return {
        defaultService: defaultService.name,
        backendServices,
        healthChecks,
        hostRules,
        listeners,
        network: loadBalancer.network,
        region: loadBalancer.region,
        urlMapName: loadBalancer.urlMapName,
        credentials: loadBalancer.credentials || loadBalancer.account,
      };
    }

    function getHealthChecksAndBackendServices(loadBalancer) {
      const defaultService = loadBalancer.defaultService;
      let backendServices = [loadBalancer.defaultService];

      if (loadBalancer.hostRules) {
        backendServices = loadBalancer.hostRules.reduce((services, hostRule) => {
          services = services.concat(hostRule.pathMatcher.defaultService);
          return hostRule.pathMatcher.pathRules.reduce(
            (services, pathRule) => services.concat(pathRule.backendService),
            services,
          );
        }, backendServices);
      }

      const healthChecks = _.chain(backendServices).map('healthCheck').uniqBy('name').cloneDeep().value();
      healthChecks.forEach((hc) => {
        hc.account = loadBalancer.account || loadBalancer.credentials;
      });
      backendServices = _.uniqBy(backendServices, 'name');

      backendServices.forEach((service) => {
        // Map health check to health check name so we don't have to deal with object references
        service.healthCheck = service.healthCheck.name;
        // Map session affinity code to more human readable text.
        service.sessionAffinity = sessionAffinityModelToViewMap[service.sessionAffinity] || service.sessionAffinity;
      });

      return { backendServices, healthChecks, defaultService };
    }

    function getHostRules(loadBalancer) {
      return mapBackendServicesToNames(_.cloneDeep(loadBalancer.hostRules));
    }

    function getListeners(loadBalancer) {
      loadBalancer.listeners.forEach((listener) => {
        const { stack, freeFormDetails } = NameUtils.parseLoadBalancerName(listener.name);
        listener.stack = stack;
        listener.detail = freeFormDetails;
        listener.created = true;
      });

      return loadBalancer.listeners;
    }

    function mapBackendServicesToNames(hostRules) {
      // Map backend service to backend service name so we don't have to deal with object references
      hostRules.forEach((hostRule) => {
        const p = hostRule.pathMatcher;

        p.defaultService = p.defaultService.name;

        p.pathRules.forEach((pathRule) => {
          pathRule.backendService = pathRule.backendService.name;
        });
      });

      return hostRules;
    }

    return { serialize, deserialize };
  },
);
