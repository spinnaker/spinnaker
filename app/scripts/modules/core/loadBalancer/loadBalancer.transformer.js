'use strict';

import * as _ from 'lodash';
let angular = require('angular');

module.exports = angular.module('spinnaker.core.loadBalancer.transformer', [
  require('../cloudProvider/serviceDelegate.service.js'),
])
  .factory('loadBalancerTransformer', function (serviceDelegate) {

    function normalizeLoadBalancer(loadBalancer) {
      return serviceDelegate.getDelegate(loadBalancer.provider || loadBalancer.type, 'loadBalancer.transformer').
        normalizeLoadBalancer(loadBalancer);
    }

    function normalizeLoadBalancerSet(loadBalancers) {
      let setNormalizers = _.chain(loadBalancers)
        .filter((lb) => serviceDelegate.hasDelegate(lb.provider || lb.type, 'loadBalancer.setTransformer'))
        .compact()
        .map((lb) => serviceDelegate
          .getDelegate(lb.provider || lb.type, 'loadBalancer.setTransformer').normalizeLoadBalancerSet)
        .uniq()
        .value();

      if (setNormalizers.length) {
        return _.flow(setNormalizers)(loadBalancers);
      } else {
        return loadBalancers;
      }
    }

    return {
      normalizeLoadBalancer: normalizeLoadBalancer,
      normalizeLoadBalancerSet: normalizeLoadBalancerSet,
    };

  });
