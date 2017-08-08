'use strict';

import _ from 'lodash';

const angular = require('angular');

import { ACCOUNT_SERVICE } from '@spinnaker/core';
import { DcosProviderSettings } from '../dcos.settings';

module.exports = angular.module('spinnaker.dcos.loadBalancer.transformer', [ACCOUNT_SERVICE])
  .factory('dcosLoadBalancerTransformer', function (accountService, $q) {
    function normalizeLoadBalancer(loadBalancer) {
      loadBalancer.provider = loadBalancer.type;
      loadBalancer.instances = [];
      loadBalancer.instanceCounts = buildInstanceCounts(loadBalancer.serverGroups);
      return $q.resolve(loadBalancer);
    }

    function attemptToSetValidAccount(defaultAccount, defaultDcosCluster, loadBalancer) {
      return accountService.getCredentialsKeyedByAccount('dcos').then(function(dcosAccountsByName) {
        var dcosAccountNames = _.keys(dcosAccountsByName);
        var firstDcosAccount = null;

        if (dcosAccountNames.length) {
          firstDcosAccount = dcosAccountNames[0];
        }

        var defaultAccountIsValid = defaultAccount && dcosAccountNames.includes(defaultAccount);

        loadBalancer.account =
          defaultAccountIsValid ? defaultAccount : (firstDcosAccount ? firstDcosAccount : 'my-dcos-account');

        attemptToSetValidDcosCluster(dcosAccountsByName, defaultDcosCluster, loadBalancer);
      });
    }

    function attemptToSetValidDcosCluster(dcosAccountsByName, defaultDcosCluster, loadBalancer) {
      var selectedAccount = dcosAccountsByName[loadBalancer.account];
      if (selectedAccount) {
        var clusterNames = _.map(selectedAccount.dcosClusters, 'name');
        var defaultDcosClusterIsValid = defaultDcosCluster && clusterNames.includes(defaultDcosCluster);
        loadBalancer.dcosCluster = defaultDcosClusterIsValid ? defaultDcosCluster : (clusterNames.length == 1 ? clusterNames[0] : null);
        loadBalancer.region = loadBalancer.dcosCluster;
      }
    }

    function buildInstanceCounts(serverGroups) {
      let instanceCounts = _.chain(serverGroups)
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
          }
        )
        .value();

      instanceCounts.outOfService += _.chain(serverGroups)
        .map('detachedInstances')
        .flatten()
        .value()
        .length;

      return instanceCounts;
    }

    function constructNewLoadBalancerTemplate() {
      var defaultAccount = DcosProviderSettings.defaults.account;
      var defaultDcosCluster = DcosProviderSettings.defaults.dcosCluster;

      var loadBalancer = {
        provider: 'dcos',
        bindHttpHttps: true,
        cpus: 2,
        instances: 1,
        mem: 1024,
        acceptedResourceRoles: ['slave_public'],
        portRange: {
          protocol: 'tcp',
          minPort: 10000,
          maxPort: 10100
        }
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
      convertLoadBalancerForEditing: convertLoadBalancerForEditing
    };
  });
