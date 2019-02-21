'use strict';

const angular = require('angular');

import { chain, flow } from 'lodash';

import { AccountService } from 'core/account/AccountService';
import { PROVIDER_SERVICE_DELEGATE } from 'core/cloudProvider/providerService.delegate';

module.exports = angular
  .module('spinnaker.core.loadBalancer.transformer', [PROVIDER_SERVICE_DELEGATE])
  .factory('loadBalancerTransformer', [
    'providerServiceDelegate',
    function(providerServiceDelegate) {
      function normalizeLoadBalancer(loadBalancer) {
        return AccountService.getAccountDetails(loadBalancer.account).then(accountDetails => {
          return providerServiceDelegate
            .getDelegate(
              loadBalancer.provider || loadBalancer.type,
              'loadBalancer.transformer',
              accountDetails && accountDetails.skin,
            )
            .normalizeLoadBalancer(loadBalancer);
        });
      }

      function normalizeLoadBalancerSet(loadBalancers) {
        let setNormalizers = chain(loadBalancers)
          .filter(lb => providerServiceDelegate.hasDelegate(lb.provider || lb.type, 'loadBalancer.setTransformer'))
          .compact()
          .map(
            lb =>
              providerServiceDelegate.getDelegate(lb.provider || lb.type, 'loadBalancer.setTransformer')
                .normalizeLoadBalancerSet,
          )
          .uniq()
          .value();

        if (setNormalizers.length) {
          return flow(setNormalizers)(loadBalancers);
        } else {
          return loadBalancers;
        }
      }

      return {
        normalizeLoadBalancer: normalizeLoadBalancer,
        normalizeLoadBalancerSet: normalizeLoadBalancerSet,
      };
    },
  ]);
