'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('instanceTypeService', function ($http, $q, settings, _, scheduledCache) {

    function getAllTypesByRegion() {
      return $http({
        url: settings.awsMetadataUrl + '/instanceType',
        cache: scheduledCache,
      });
    }

    function getAvailableTypesForRegions(selectedRegions) {
      selectedRegions = selectedRegions || [];
      return getAllTypesByRegion().then(function(instanceTypes) {
        var availableTypes = [];
        instanceTypes.data.forEach(function(instanceType) {
          if (_.intersection(selectedRegions, instanceType.regions).length === selectedRegions.length) {
            availableTypes.push(instanceType.name);
          }
        });
        return availableTypes.sort();
      });
    }

    function getAvailableRegionsForType(instanceType) {
      return getAllTypesByRegion().then(function(data) {
        var instance = _.find(data, { name: instanceType });
        return instance ? instance.regions : [];
      });
    }

    return {
      getAvailableTypesForRegions: getAvailableTypesForRegions,
      getAvailableRegionsForType: getAvailableRegionsForType,
    };
  }
);
