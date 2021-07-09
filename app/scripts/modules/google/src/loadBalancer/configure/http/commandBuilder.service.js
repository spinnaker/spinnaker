'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { AccountService, LOAD_BALANCER_READ_SERVICE, NetworkReader, SubnetReader } from '@spinnaker/core';

import { GCE_ADDRESS_READER } from '../../../address/address.reader';
import { GOOGLE_BACKENDSERVICE_BACKENDSERVICE_READER } from '../../../backendService/backendService.reader';
import { GCE_CERTIFICATE_READER } from '../../../certificate/certificate.reader';
import { sessionAffinityModelToViewMap } from '../common/sessionAffinityNameMaps';
import { GOOGLE_COMMON_XPNNAMING_GCE_SERVICE } from '../../../common/xpnNaming.gce.service';
import { GCEProviderSettings } from '../../../gce.settings';
import { GCE_HEALTH_CHECK_READER } from '../../../healthCheck/healthCheck.read.service';
import { GCE_HTTP_LOAD_BALANCER_UTILS } from '../../httpLoadBalancerUtils.service';
import { HttpLoadBalancerTemplate, ListenerTemplate } from './templates';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_TRANSFORMER_SERVICE } from './transformer.service';

export const GOOGLE_LOADBALANCER_CONFIGURE_HTTP_COMMANDBUILDER_SERVICE =
  'spinnaker.deck.gce.httpLoadBalancer.backing.service';
export const name = GOOGLE_LOADBALANCER_CONFIGURE_HTTP_COMMANDBUILDER_SERVICE; // for backwards compatibility
module(GOOGLE_LOADBALANCER_CONFIGURE_HTTP_COMMANDBUILDER_SERVICE, [
  GOOGLE_BACKENDSERVICE_BACKENDSERVICE_READER,
  GCE_CERTIFICATE_READER,
  LOAD_BALANCER_READ_SERVICE,
  GCE_HTTP_LOAD_BALANCER_UTILS,
  GCE_ADDRESS_READER,
  GCE_HEALTH_CHECK_READER,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_TRANSFORMER_SERVICE,
  GOOGLE_COMMON_XPNNAMING_GCE_SERVICE,
]).factory('gceHttpLoadBalancerCommandBuilder', [
  '$q',
  'gceHttpLoadBalancerUtils',
  'gceBackendServiceReader',
  'gceCertificateReader',
  'gceHealthCheckReader',
  'gceHttpLoadBalancerTransformer',
  'loadBalancerReader',
  'gceAddressReader',
  'gceXpnNamingService',
  function (
    $q,
    gceHttpLoadBalancerUtils,
    gceBackendServiceReader,
    gceCertificateReader,
    gceHealthCheckReader,
    gceHttpLoadBalancerTransformer,
    loadBalancerReader,
    gceAddressReader,
    gceXpnNamingService,
  ) {
    function buildCommand({ originalLoadBalancer, isNew, isInternal }) {
      originalLoadBalancer = _.cloneDeep(originalLoadBalancer);

      return buildBackingDataAndLoadBalancer(originalLoadBalancer, isNew, isInternal).then(
        ({ backingData, loadBalancer }) => {
          return {
            backingData,
            getAllBackendServices,
            isNew,
            loadBalancer,
            onAccountChange,
            onBackendServiceRefresh,
            onBackendServiceSelected,
            onRegionSelected,
            onCertificateRefresh,
            onHealthCheckRefresh,
            onHealthCheckSelected,
            getUnusedBackendServices,
            removeUnusedBackendServices,
            getUnusedHealthChecks,
            removeUnusedHealthChecks,
            onAddressRefresh,
          };
        },
      );
    }

    function buildBackingDataAndLoadBalancer(originalLoadBalancer, isNew, isInternal) {
      let region = null;
      if (isInternal) {
        region = originalLoadBalancer ? originalLoadBalancer.region : GCEProviderSettings.defaults.region;
      }
      return buildBackingData(region).then((backingData) => {
        const loadBalancer = buildLoadBalancer(isNew, originalLoadBalancer, isInternal);

        unifyDataSources(backingData, loadBalancer);

        return { backingData, loadBalancer };
      });
    }

    function buildBackingData(region) {
      const promises = {
        backendServices: getBackendServices(region),
        healthChecks: getHealthChecks(region),
        certificates: getCertificates(),
        loadBalancerMap: getLoadBalancerMap(region),
        networks: getNetworks(),
        subnets: SubnetReader.listSubnetsByProvider('gce'),
        addresses: gceAddressReader.listAddresses(region),
        accounts: getAccounts(),
      };
      return $q.all(promises);
    }

    function buildLoadBalancer(isNew, loadBalancer, isInternal) {
      const loadBalancerTemplate = new HttpLoadBalancerTemplate(GCEProviderSettings.defaults.account || null);
      if (isInternal) {
        loadBalancerTemplate.loadBalancerType = 'INTERNAL_MANAGED';
        loadBalancerTemplate.isInternal = true;
        if (loadBalancer) {
          loadBalancer.network = gceXpnNamingService.decorateXpnResourceIfNecessary(
            loadBalancer.project,
            loadBalancer.network,
          );
          loadBalancer.listeners.forEach((listener) => {
            listener.subnet = gceXpnNamingService.decorateXpnResourceIfNecessary(loadBalancer.project, listener.subnet);
          });
        }
        loadBalancerTemplate.region = GCEProviderSettings.defaults.region;
        loadBalancerTemplate.network = 'default';
      } else {
        loadBalancerTemplate.loadBalancerType = 'HTTP';
        loadBalancerTemplate.isInternal = false;
      }

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

      const loadBalancerData = _.assign(loadBalancerTemplate, mixinData);
      return loadBalancerData;
    }

    function unifyDataSources(backingData, loadBalancer) {
      setAccount(backingData.accounts, loadBalancer);
      removeExistingListenersFromBackingData(backingData, loadBalancer.listeners);

      // update backing data with any values coming from load balancer -- they are more up to date.
      const lbHealthCheckMap = _.keyBy(loadBalancer.healthChecks, 'name');
      const backingDataHealthCheckMap = _.keyBy(backingData.healthChecks, 'name');

      backingData.healthChecksKeyedByName = _.assign(backingDataHealthCheckMap, _.cloneDeep(lbHealthCheckMap));
      backingData.healthChecksKeyedByNameCopy = _.cloneDeep(backingDataHealthCheckMap);
      backingData.healthChecks = _.map(backingDataHealthCheckMap, _.identity);

      const lbBackendServicesMap = _.keyBy(loadBalancer.backendServices, 'name');
      const backingDataBackendServiceMap = _.keyBy(backingData.backendServices, 'name');

      backingData.backendServicesKeyedByName = _.assign(
        backingDataBackendServiceMap,
        _.cloneDeep(lbBackendServicesMap),
      );
      backingData.backendServicesKeyedByNameCopy = _.cloneDeep(backingDataBackendServiceMap);
      backingData.backendServices = _.map(backingDataBackendServiceMap, _.identity);

      backingData.regions = backingData.accounts
        .find((account) => account.name === loadBalancer.credentials)
        .regions.map((region) => region.name);

      backingData.subnetMap = _.groupBy(backingData.subnets, 'network');
      backingData.internalHttpLbNetworks = backingData.networks.filter((network) =>
        backingData.subnetMap[network].find((subnet) => subnet.purpose === 'INTERNAL_HTTPS_LOAD_BALANCER'),
      );
    }

    function removeExistingListenersFromBackingData(backingData, existingListeners) {
      const accountNames = backingData.accounts.map((account) => account.name);

      accountNames.forEach((accountName) => {
        if (_.has(backingData, ['loadBalancerMap', accountName, 'listeners'])) {
          backingData.loadBalancerMap[accountName].listeners = _.without(
            backingData.loadBalancerMap[accountName].listeners,
            ...existingListeners.map((listener) => listener.name),
          );
        }
      });
    }

    function setAccount(accounts, loadBalancerData) {
      const accountNames = _.map(accounts, 'name');
      const credentials = _.get(loadBalancerData, 'credentials.name') || loadBalancerData.credentials;

      if (!accountNames.includes(credentials)) {
        loadBalancerData.credentials = _.first(accountNames);
      }
    }

    function getHealthChecks(region) {
      return gceHealthCheckReader.listHealthChecks().then((healthChecks) => {
        return healthChecks.filter((healthCheck) => !region || healthCheck.region === region);
      });
    }

    function getNetworks() {
      return NetworkReader.listNetworksByProvider('gce').then((networks) => {
        return _.chain(networks).map('name').compact().uniq().value();
      });
    }

    function getBackendServices(region) {
      const kind = region ? 'regionBackendService' : 'globalBackendService';
      return gceBackendServiceReader.listBackendServices(kind).then((backendServices) => {
        backendServices = backendServices.filter((backendService) => !region || backendService.region === region);
        backendServices.forEach((service) => {
          service.healthCheck = service.healthCheckLink.split('/').pop();

          const ttlIsDefined = typeof service.affinityCookieTtlSec === 'string';
          service.affinityCookieTtlSec = ttlIsDefined ? Number(service.affinityCookieTtlSec) : null;

          service.sessionAffinity = sessionAffinityModelToViewMap[service.sessionAffinity] || service.sessionAffinity;
        });

        return backendServices;
      });
    }

    function getCertificates() {
      return gceCertificateReader.listCertificates();
    }

    function getAccounts() {
      return AccountService.listAccounts('gce');
    }

    function getLoadBalancerMap(region) {
      return loadBalancerReader.listLoadBalancers('gce').then((lbs) => {
        return _.chain(lbs)
          .map((lb) => lb.accounts)
          .flatten()
          .groupBy('name')
          .mapValues((accounts) => {
            const loadBalancers = _.chain(accounts)
              .map((a) => a.regions)
              .flatten()
              .filter((region) => region.name === (region || gceHttpLoadBalancerUtils.REGION))
              .map((region) => region.loadBalancers)
              .flatten()
              .value();

            const urlMapNames = _.chain(loadBalancers).map('urlMapName').uniq().value();
            const listeners = _.chain(loadBalancers).map('name').uniq().value();

            return { urlMapNames, listeners };
          })
          .valueOf();
      });
    }

    function onHealthCheckRefresh(command) {
      getHealthChecks(command.loadBalancer.region).then((healthChecks) => {
        command.backingData.healthChecks = healthChecks;
        command.backingData.healthChecksKeyedByName = _.keyBy(healthChecks, 'name');
        command.backingData.healthChecksKeyedByNameCopy = _.cloneDeep(command.backingData.healthChecksKeyedByName);

        command.loadBalancer.healthChecks = command.loadBalancer.healthChecks.map((hc) => {
          const updated = command.backingData.healthChecksKeyedByName[_.get(hc, 'name')];
          if (updated) {
            return _.cloneDeep(updated);
          } else {
            return hc;
          }
        });
      });
    }

    function onCertificateRefresh(command) {
      getCertificates().then((certificates) => {
        command.backingData.certificates = certificates;
      });
    }

    function onBackendServiceRefresh(command) {
      getBackendServices(command.loadBalancer.region).then((backendServices) => {
        command.backingData.backendServices = backendServices;
        command.backingData.backendServicesKeyedByName = _.keyBy(backendServices, 'name');
        command.backingData.backendServicesKeyedByNameCopy = _.cloneDeep(
          command.backingData.backendServicesKeyedByName,
        );

        command.loadBalancer.backendServices = command.loadBalancer.backendServices.map((service) => {
          const updated = command.backingData.backendServicesKeyedByName[_.get(service, 'name')];
          if (updated) {
            return _.cloneDeep(updated);
          } else {
            return service;
          }
        });
      });
    }

    function onRegionSelected(command) {
      buildBackingData(command.loadBalancer.region).then((backingData) => {
        Object.assign(command.backingData, backingData);
        unifyDataSources(command.backingData, command.loadBalancer);
      });
    }

    function onHealthCheckSelected(selectedName, command) {
      if (!command.loadBalancer.healthChecks.find((hc) => _.get(hc, 'name') === selectedName)) {
        const selectedObject = command.backingData.healthChecksKeyedByName[selectedName];
        if (selectedObject) {
          command.loadBalancer.healthChecks.push(selectedObject);
        }
      }
    }

    function onBackendServiceSelected(selectedName, command) {
      if (!command.loadBalancer.backendServices.find((service) => service.name === selectedName)) {
        const selectedObject = command.backingData.backendServicesKeyedByName[selectedName];
        command.loadBalancer.backendServices.push(selectedObject);
        if (selectedObject.healthCheck) {
          onHealthCheckSelected(selectedObject.healthCheck, command);
        }
      }
    }

    function getAllBackendServices(command) {
      const allBackendServices = command.loadBalancer.backendServices.concat(command.backingData.backendServices);
      return _.chain(allBackendServices)
        .filter((service) => {
          return (
            service.account === command.loadBalancer.credentials || service.account === command.loadBalancer.account
          );
        })
        .map('name')
        .compact()
        .uniq()
        .value();
    }

    function getUnusedHealthChecks(command) {
      return _.chain(command.loadBalancer.healthChecks)
        .map('name')
        .difference(_.map(command.loadBalancer.backendServices, 'healthCheck'))
        .compact()
        .uniq()
        .value();
    }

    function getUnusedBackendServices(command) {
      const defaultService = command.loadBalancer.defaultService;
      const hostRuleServices = _.map(command.loadBalancer.hostRules, 'pathMatcher.defaultService');
      const pathRuleServices = _.chain(command.loadBalancer.hostRules)
        .map('pathMatcher.pathRules')
        .flatten()
        .map('backendService')
        .value();

      const usedServices = _.chain([defaultService, ...hostRuleServices, ...pathRuleServices])
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

    function removeUnusedBackendServices(command) {
      const unusedBackendServices = getUnusedBackendServices(command);
      command.loadBalancer.backendServices = command.loadBalancer.backendServices.filter(
        (service) => !unusedBackendServices.includes(service.name),
      );
    }

    function removeUnusedHealthChecks(command) {
      const unusedHealthChecks = getUnusedHealthChecks(command);
      command.loadBalancer.healthChecks = command.loadBalancer.healthChecks.filter(
        (healthCheck) => !unusedHealthChecks.includes(healthCheck.name),
      );
    }

    function onAccountChange(command) {
      command.loadBalancer.backendServices = [];
      command.loadBalancer.healthChecks = [];
      command.loadBalancer.hostRules = [];
      command.loadBalancer.listeners = [new ListenerTemplate()];
      command.loadBalancer.defaultService = null;
      command.backingData.regions = command.backingData.accounts
        .find((account) => account.name === command.loadBalancer.credentials)
        .regions.map((region) => region.name);
    }

    function onAddressRefresh(command) {
      gceAddressReader.listAddresses('global').then((addresses) => {
        command.backingData.addresses = addresses;
      });
    }

    return { buildCommand };
  },
]);
