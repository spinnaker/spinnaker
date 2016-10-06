'use strict';

import {HttpLoadBalancerTemplate, ListenerTemplate} from './templates';
import * as _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.httpLoadBalancer.backing.service', [
    require('../../../backendService/backendService.reader.js'),
    require('../../../../core/cache/cacheInitializer.js'),
    require('../../../certificate/certificate.reader.js'),
    require('../../../../core/account/account.service.js'),
    require('../../../../core/loadBalancer/loadBalancer.read.service.js'),
    require('../../elSevenUtils.service.js'),
    require('../../../httpHealthCheck/httpHealthCheck.reader.js'),
    require('./transformer.service.js'),
  ])
  .factory('gceHttpLoadBalancerCommandBuilder', function ($q, accountService,
                                                          cacheInitializer,
                                                          elSevenUtils,
                                                          gceBackendServiceReader,
                                                          gceCertificateReader,
                                                          gceHttpHealthCheckReader,
                                                          gceHttpLoadBalancerTransformer,
                                                          loadBalancerReader, settings) {

    function buildCommand ({ originalLoadBalancer, isNew }) {
      originalLoadBalancer = _.cloneDeep(originalLoadBalancer);

      return buildBackingDataAndLoadBalancer(originalLoadBalancer, isNew)
        .then(({ backingData, loadBalancer}) => {
          return {
            backingData,
            getAllBackendServices,
            isNew,
            loadBalancer,
            onBackendServiceRefresh,
            onBackendServiceSelected,
            onCertificateRefresh,
            onHealthCheckRefresh,
            onHealthCheckSelected,
            getUnusedBackendServices,
            removeUnusedBackendServices,
            getUnusedHealthChecks,
            removeUnusedHealthChecks,
          };
        });
    }

    function buildBackingDataAndLoadBalancer (originalLoadBalancer, isNew) {
      return $q.all({
        backendServices: getBackendServices(),
        healthChecks: getHealthChecks(),
        certificates: getCertificates(),
        loadBalancerMap: getLoadBalancerMap(),
        accounts: getAccounts(),
      }).then((backingData) => {
        let loadBalancer = buildLoadBalancer(isNew, originalLoadBalancer, backingData);

        unifyDataSources(backingData, loadBalancer);

        return { backingData, loadBalancer };
      });
    }

    function buildLoadBalancer (isNew, loadBalancer) {
      let loadBalancerTemplate =
        new HttpLoadBalancerTemplate(_.get(settings, 'providers.gce.defaults.account') || null);

      let mixinData;
      if (isNew) {
        mixinData = {
          backendServices: [],
          listeners: [new ListenerTemplate()],
          healthChecks: [],
        };
      } else {
        mixinData = gceHttpLoadBalancerTransformer.deserialize(loadBalancer);
      }

      let loadBalancerData = _.assign(loadBalancerTemplate, mixinData);
      return loadBalancerData;
    }

    function unifyDataSources (backingData, loadBalancer) {
      setAccount(backingData.accounts, loadBalancer);
      removeExistingListenersFromBackingData(backingData, loadBalancer.listeners);

      // update backing data with any values coming from load balancer -- they are more up to date.
      let lbHealthCheckMap = _.keyBy(loadBalancer.healthChecks, 'name');
      let backingDataHealthCheckMap = _.keyBy(backingData.healthChecks, 'name');

      backingData.healthChecksKeyedByName = _.assign(backingDataHealthCheckMap, _.cloneDeep(lbHealthCheckMap));
      backingData.healthChecksKeyedByNameCopy = _.cloneDeep(backingDataHealthCheckMap);
      backingData.healthChecks = _.map(backingDataHealthCheckMap, _.identity);

      let lbBackendServicesMap = _.keyBy(loadBalancer.backendServices, 'name');
      let backingDataBackendServiceMap = _.keyBy(backingData.backendServices, 'name');

      backingData.backendServicesKeyedByName = _.assign(backingDataBackendServiceMap, _.cloneDeep(lbBackendServicesMap));
      backingData.backendServicesKeyedByNameCopy = _.cloneDeep(backingDataBackendServiceMap);
      backingData.backendServices = _.map(backingDataBackendServiceMap, _.identity);
    }

    function removeExistingListenersFromBackingData (backingData, existingListeners) {
      let accountNames = backingData.accounts.map(account => account.name);

      accountNames.forEach((accountName) => {
        backingData.loadBalancerMap[accountName].listeners =
          _.without(
            backingData.loadBalancerMap[accountName].listeners,
            ...existingListeners.map(listener => listener.name));
      });
    }

    function setAccount (accounts, loadBalancerData) {
      let accountNames = _.map(accounts, 'name');
      let credentials = _.get(loadBalancerData, 'credentials.name') || loadBalancerData.credentials;

      if (!accountNames.includes(credentials)) {
        loadBalancerData.credentials = _.first(accountNames);
      }
    }

    function getHealthChecks () {
      return gceHttpHealthCheckReader.listHttpHealthChecks()
        .then(([response]) => response.results.map((hc) => JSON.parse(hc.httpHealthCheck)));
    }

    function getBackendServices () {
      return gceBackendServiceReader.listBackendServices()
        .then(([response]) => {
          let backendServices = response.results;
          backendServices.forEach((service) => {
            service.healthCheck = service.healthCheckLink.split('/').pop();

            let ttlIsDefined = typeof service.affinityCookieTtlSec === 'string';
            service.affinityCookieTtlSec = ttlIsDefined ? Number(service.affinityCookieTtlSec) : null;
          });

          return backendServices;
        });
    }

    function getCertificates () {
      return gceCertificateReader.listCertificates()
        .then(([response]) => response.results.map(c => c.name));
    }

    function getAccounts () {
      return accountService
        .listAccounts('gce');
    }

    function getLoadBalancerMap () {
      return loadBalancerReader
        .listLoadBalancers('gce')
        .then((lbs) => {
          return _.chain(lbs)
            .map(lb => lb.accounts)
            .flatten()
            .groupBy('name')
            .mapValues((accounts) => {
              let loadBalancers = _.chain(accounts)
                .map(a => a.regions)
                .flatten()
                .filter(region => region.name === elSevenUtils.getElSevenRegion())
                .map(region => region.loadBalancers)
                .flatten()
                .value();

              let urlMapNames = _.chain(loadBalancers).map('urlMapName').uniq().value();
              let listeners = _.chain(loadBalancers).map('name').uniq().value();

              return { urlMapNames, listeners };
            })
            .valueOf();
        });
    }

    function onHealthCheckRefresh (command) {
      getHealthChecks()
        .then((healthChecks) => {
          command.backingData.healthChecks = healthChecks;
          command.backingData.healthChecksKeyedByName = _.keyBy(healthChecks, 'name');
          command.backingData.healthChecksKeyedByNameCopy =
            _.cloneDeep(command.backingData.healthChecksKeyedByName);

          command.loadBalancer.healthChecks =
            command.loadBalancer.healthChecks
              .map((hc) => {
                let updated = command.backingData.healthChecksKeyedByName[_.get(hc,'name')];
                if (updated) {
                  return _.cloneDeep(updated);
                } else {
                  return hc;
                }
              });
        });
    }

    function onCertificateRefresh (command) {
      getCertificates()
        .then((certificates) => {
          command.backingData.certificates = certificates;
        });
    }

    function onBackendServiceRefresh (command) {
      getBackendServices()
        .then((backendServices) => {
          command.backingData.backendServices = backendServices;
          command.backingData.backendServicesKeyedByName = _.keyBy(backendServices, 'name');
          command.backingData.backendServicesKeyedByNameCopy =
            _.cloneDeep(command.backingData.backendServicesKeyedByName);

          command.loadBalancer.backendServices =
            command.loadBalancer.backendServices
              .map((service) => {
                let updated = command.backingData.backendServicesKeyedByName[_.get(service, 'name')];
                if (updated) {
                  return _.cloneDeep(updated);
                } else {
                  return service;
                }
              });
        });
    }

    function onHealthCheckSelected (selectedName, command) {
      if (!command.loadBalancer.healthChecks.find((hc) => _.get(hc, 'name') === selectedName)) {
        let selectedObject = command.backingData.healthChecksKeyedByName[selectedName];
        if (selectedObject) {
          command.loadBalancer.healthChecks.push(selectedObject);
        }
      }
    }

    function onBackendServiceSelected (selectedName, command) {
      if (!command.loadBalancer.backendServices.find((service) => service.name === selectedName)) {
        let selectedObject = command.backingData.backendServicesKeyedByName[selectedName];
        command.loadBalancer.backendServices.push(selectedObject);
        if (selectedObject.healthCheck) {
          onHealthCheckSelected(selectedObject.healthCheck, command);
        }
      }
    }

    function getAllBackendServices (command) {
      let allBackendServices = command.loadBalancer.backendServices.concat(command.backingData.backendServices);
      return _.chain(allBackendServices)
        .map('name')
        .compact()
        .uniq()
        .value();
    }

    function getUnusedHealthChecks (command) {
      return _.chain(command.loadBalancer.healthChecks)
        .map('name')
        .difference(_.map(command.loadBalancer.backendServices, 'healthCheck'))
        .compact()
        .uniq()
        .value();
    }

    function getUnusedBackendServices (command) {
      let defaultService = command.loadBalancer.defaultService;
      let hostRuleServices = _.map(command.loadBalancer.hostRules, 'pathMatcher.defaultService');
      let pathRuleServices = _.chain(command.loadBalancer.hostRules)
        .map('pathMatcher.pathRules')
        .flatten()
        .map('backendService')
        .value();

      let usedServices = _.chain([defaultService, ...hostRuleServices, ...pathRuleServices])
        .compact()
        .uniq()
        .value();

      return _.chain(command.loadBalancer.backendServices)
        .map('name')
        .difference(usedServices)
        .compact()
        .uniq()
        .value();
    }

    function removeUnusedBackendServices (command) {
      let unusedBackendServices = getUnusedBackendServices(command);
      command.loadBalancer.backendServices = command.loadBalancer.backendServices
        .filter((service) => !unusedBackendServices.includes(service.name));
    }

    function removeUnusedHealthChecks (command) {
      let unusedHealthChecks = getUnusedHealthChecks(command);
      command.loadBalancer.healthChecks = command.loadBalancer.healthChecks
        .filter((healthCheck) => !unusedHealthChecks.includes(healthCheck.name));
    }

    return { buildCommand };
  });
