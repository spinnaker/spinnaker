'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.httpLoadBalancer.editStateUtils.service', [
    require('../../../../core/utils/lodash.js')
  ])
  .factory('gceHttpLoadBalancerEditStateUtils', function (_) {

    function getBackingData (lb) {
      let backendServices = getBackendServices(lb);
      let healthChecks = getHealthChecks(backendServices);
      let hostRules = getHostRules(lb);

      reconcileObjectReferences(lb, backendServices, healthChecks);
      normalizeNames(backendServices, healthChecks);
      normalizeLoadBalancer(lb);

      return { backendServices, healthChecks, hostRules };
    }

    function getBackendServices (lb) {
      let backendServices = [getAndMarkDefaultBackend(lb)];

      if (lb.hostRules) {
        backendServices = _.uniq(
          lb.hostRules
            .reduce((services, hostRule) => {
              services = services.concat(hostRule.pathMatcher.defaultService);
              return hostRule.pathMatcher.pathRules
                .reduce((services, pathRule) => services.concat(pathRule.backendService), services);
            }, backendServices),
          (service) => service.name);
      }

      return backendServices;
    }

    function getAndMarkDefaultBackend (lb) {
      let s = lb.defaultService;
      s.useAsDefault = true;
      return s;
    }

    function getHealthChecks (services) {
      return _(services)
        .map((s) => s.healthCheck)
        .uniq((healthCheck) => healthCheck.name)
        .valueOf();
    }

    function getHostRules (lb) {
      return lb.hostRules;
    }

    function reconcileObjectReferences (lb, backendServices, healthChecks) {
      reconcileBackendServiceReferences(lb, backendServices);
      reconcileHealthCheckReferences(backendServices, healthChecks);
    }

    function reconcileBackendServiceReferences (lb, backendServices) {
      let servicesByName = _(backendServices)
        .groupBy('name')
        .mapValues((services) => _.first(services))
        .valueOf();

      /*
        places to spot a backend service:
          1). loadBalancer.defaultService
          2). hostRule.pathMatcher.defaultService
          3). pathRule.backendService
      */

      lb.defaultService = servicesByName[lb.defaultService.name];

      lb.hostRules.forEach((hostRule) => {
        let p = hostRule.pathMatcher;

        p.defaultService = servicesByName[p.defaultService.name];

        p.pathRules.forEach((pathRule) => {
          pathRule.backendService = servicesByName[pathRule.backendService.name];
        });
      });
    }

    function reconcileHealthCheckReferences (backendServices, healthChecks) {
      let healthChecksByName = _(healthChecks)
        .groupBy('name')
        .mapValues((checks) => _.first(checks))
        .valueOf();

      backendServices.forEach((service) => {
        service.healthCheck = healthChecksByName[service.healthCheck.name];
      });
    }

    function normalizeNames (backendServices, healthChecks) {
      let components = [...backendServices, ...healthChecks];
      components.forEach((c) => c.name = parseComponentName(c.name));
    }

    function normalizeLoadBalancer (lb) {
      lb.portRange = parseComponentName(lb.portRange);
      delete lb.instances;
    }

    function parseComponentName (name) {
      return name.split('-').pop();
    }

    return { getBackingData };
  });
