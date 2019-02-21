'use strict';

const angular = require('angular');
import _ from 'lodash';

import { API, InfrastructureCaches } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.openstack.instanceType.service', [])
  .factory('openstackInstanceTypeService', ['$http', '$q', function($http, $q) {
    var categories = [
      {
        type: 'custom',
        label: 'Custom Type',
        families: [],
        icon: 'asterisk',
      },
    ];

    function getCategories() {
      return $q.when(categories);
    }

    var getAllTypesByRegion = function getAllTypesByRegion() {
      var cached = InfrastructureCaches.get('instanceTypes').get('openstack');
      if (cached) {
        return $q.when(cached);
      }
      return API.one('instanceTypes')
        .get()
        .then(function(types) {
          var result = _.chain(types)
            .map(function(type) {
              return {
                region: type.region,
                account: type.account,
                name: type.name,
                key: [type.region, type.account, type.name].join(':'),
              };
            })
            .uniqBy('key')
            .groupBy('region')
            .value();
          InfrastructureCaches.get('instanceTypes').put('openstack', result);
          return result;
        });
    };

    function getAvailableTypesForRegions(availableRegions, selectedRegions) {
      selectedRegions = selectedRegions || [];
      var availableTypes = [];

      // prime the list of available types
      if (selectedRegions && selectedRegions.length) {
        availableTypes = _.map(availableRegions[selectedRegions[0]], 'name');
      }

      // this will perform an unnecessary intersection with the first region, which is fine
      selectedRegions.forEach(function(selectedRegion) {
        if (availableRegions[selectedRegion]) {
          availableTypes = _.intersection(availableTypes, _.map(availableRegions[selectedRegion], 'name'));
        }
      });

      return availableTypes.sort();
    }

    function filterInstanceTypesByVirtualizationType(instanceTypes /*, virtualizationType*/) {
      return instanceTypes;
    }

    return {
      getCategories: getCategories,
      getAvailableTypesForRegions: getAvailableTypesForRegions,
      getAllTypesByRegion: getAllTypesByRegion,
      filterInstanceTypesByVirtualizationType: filterInstanceTypesByVirtualizationType,
    };
  }]);
