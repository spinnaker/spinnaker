'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { AccountService } from '@spinnaker/core';

import { DcosProviderSettings } from '../dcos.settings';

export const DCOS_LOADBALANCER_TRANSFORMER = 'spinnaker.dcos.loadBalancer.transformer';
export const name = DCOS_LOADBALANCER_TRANSFORMER; // for backwards compatibility
module(DCOS_LOADBALANCER_TRANSFORMER, []).factory('dcosLoadBalancerTransformer', [
  '$q',
  function ($q) {
    function normalizeLoadBalancer(loadBalancer) {
      loadBalancer.provider = loadBalancer.type;
      loadBalancer.instances = [];
      loadBalancer.instanceCounts = buildInstanceCounts(loadBalancer.serverGroups);
      return $q.resolve(loadBalancer);
    }

    function attemptToSetValidAccount(defaultAccount, defaultDcosCluster, loadBalancer) {
      return AccountService.getCredentialsKeyedByAccount('dcos').then(function (dcosAccountsByName) {
        const dcosAccountNames = _.keys(dcosAccountsByName);
        let firstDcosAccount = null;

        if (dcosAccountNames.length) {
          firstDcosAccount = dcosAccountNames[0];
        }

        const defaultAccountIsValid = defaultAccount && dcosAccountNames.includes(defaultAccount);

        loadBalancer.account = defaultAccountIsValid
          ? defaultAccount
          : firstDcosAccount
          ? firstDcosAccount
          : 'my-dcos-account';

        attemptToSetValidDcosCluster(dcosAccountsByName, defaultDcosCluster, loadBalancer);
      });
    }

    function attemptToSetValidDcosCluster(dcosAccountsByName, defaultDcosCluster, loadBalancer) {
      const selectedAccount = dcosAccountsByName[loadBalancer.account];
      if (selectedAccount) {
        const clusterNames = _.map(selectedAccount.dcosClusters, 'name');
        const defaultDcosClusterIsValid = defaultDcosCluster && clusterNames.includes(defaultDcosCluster);
        loadBalancer.dcosCluster = defaultDcosClusterIsValid
          ? defaultDcosCluster
          : clusterNames.length == 1
          ? clusterNames[0]
          : null;
        loadBalancer.region = loadBalancer.dcosCluster;
      }
    }

    function buildInstanceCounts(serverGroups) {
      const instanceCounts = _.chain(serverGroups)
        .map('instances')
        .flatten()
        .reduce(
          (acc, instance) => {
            acc[_.camelCase(instance.health.state)]++;
            return acc;
          },
          {
            up: 0,
            down: 0,
            outOfService: 0,
            succeeded: 0,
            failed: 0,
            unknown: 0,
          },
        )
        .value();

      instanceCounts.outOfService += _.chain(serverGroups).map('detachedInstances').flatten().value().length;

      return instanceCounts;
    }

    function constructNewLoadBalancerTemplate() {
      const defaultAccount = DcosProviderSettings.defaults.account;
      const defaultDcosCluster = DcosProviderSettings.defaults.dcosCluster;

      const loadBalancer = {
        provider: 'dcos',
        bindHttpHttps: true,
        cpus: 2,
        instances: 1,
        mem: 1024,
        acceptedResourceRoles: ['slave_public'],
        portRange: {
          protocol: 'tcp',
          minPort: 10000,
          maxPort: 10100,
        },
      };

      attemptToSetValidAccount(defaultAccount, defaultDcosCluster, loadBalancer);

      return loadBalancer;
    }

    function convertLoadBalancerForEditing(loadBalancer) {
      return loadBalancer.description;
    }

    return {
      normalizeLoadBalancer: normalizeLoadBalancer,
      constructNewLoadBalancerTemplate: constructNewLoadBalancerTemplate,
      convertLoadBalancerForEditing: convertLoadBalancerForEditing,
    };
  },
]);
