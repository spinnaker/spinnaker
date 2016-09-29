'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.httpLoadBalancer.editStateUtils.service', [])
  .factory('gceHttpLoadBalancerEditStateUtils', function () {

    function getBackingData (lb) {
      let backendServices = getBackendServices(lb);
      let healthChecks = getHealthChecks(backendServices);
      let hostRules = getHostRules(lb);

      reconcileObjectReferences(lb, backendServices, healthChecks);
      normalizeLoadBalancer(lb);

      return { backendServices, healthChecks, hostRules };
    }

    function getBackendServices (lb) {
      let backendServices = [getAndMarkDefaultBackend(lb)];

      if (lb.hostRules) {
        backendServices = _.uniqBy(
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
      return _.chain(services)
        .map((s) => s.healthCheck)
        .uniqBy((healthCheck) => healthCheck.name)
        .value();
    }

    function getHostRules (lb) {
      return lb.hostRules;
    }

    function reconcileObjectReferences (lb, backendServices, healthChecks) {
      reconcileBackendServiceReferences(lb, backendServices);
      reconcileHealthCheckReferences(backendServices, healthChecks);
    }

    function reconcileBackendServiceReferences (lb, backendServices) {
      let servicesByName = _.chain(backendServices)
        .groupBy('name')
        .mapValues((services) => _.head(services))
        .value();

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
      let healthChecksByName = _.chain(healthChecks)
        .groupBy('name')
        .mapValues((checks) => _.head(checks))
        .value();

      backendServices.forEach((service) => {
        service.healthCheck = healthChecksByName[service.healthCheck.name];
      });
    }

    function normalizeLoadBalancer (lb) {
      lb.portRange = lb.portRange.split('-').pop();
      delete lb.instances;
    }

    return { getBackingData };
  });
