'use strict';

import _ from 'lodash';
import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.subnet.read.service', [
    API_SERVICE,
    require('../cache/infrastructureCaches.js'),
  ])
  .factory('subnetReader', function ($q, API, infrastructureCaches) {

    let cachedSubnets = null;

    function listSubnets() {
      if (cachedSubnets) {
        return $q.when(cachedSubnets);
      }
      return API
        .one('subnets')
        .useCache(infrastructureCaches.subnets)
        .getList()
        .then(function(subnets) {
          let results = subnets.map(subnet => {
            subnet.label = subnet.purpose;
            subnet.deprecated = !!subnet.deprecated;
            if (subnet.deprecated) {
              subnet.label += ' (deprecated)';
            }
            return subnet;
          });
          cachedSubnets = results;
          return results;
        });
    }

    function listSubnetsByProvider(cloudProvider) {
      return API.one('subnets')
        .one(cloudProvider)
        .useCache(infrastructureCaches.subnets)
        .getList();
    }

    function getSubnetByIdAndProvider(subnetId, cloudProvider = 'aws') {
      return listSubnetsByProvider(cloudProvider)
        .then((results) => {
          return _.head(_.filter(results, subnet => subnet.id === subnetId));
        });
    }

    function getSubnetPurpose(id) {
      return listSubnets().then(subnets => {
        let match = subnets.find(test => test.id === id);
        return match ? match.purpose : null;
      });
    }

    return {
      listSubnets: listSubnets,
      listSubnetsByProvider: listSubnetsByProvider,
      getSubnetByIdAndProvider: getSubnetByIdAndProvider,
      getSubnetPurpose: getSubnetPurpose,
    };

  });
