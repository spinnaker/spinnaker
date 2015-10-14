'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.instanceType.service', [
  require('../cache/deckCacheFactory.js'),
  require('../utils/lodash.js'),
  require('../cloudProvider/serviceDelegate.service.js'),
])
  .factory('instanceTypeService', function ($http, $q, _, $window, serviceDelegate) {

    function getDelegate(selectedProvider) {
      return serviceDelegate.getDelegate(selectedProvider, 'instance.instanceTypeService');
    }

    function getCategories(selectedProvider) {
      return getDelegate(selectedProvider).getCategories();
    }

    function getAllTypesByRegion(selectedProvider) {
      return getDelegate(selectedProvider).getAllTypesByRegion();
    }

    function getAvailableTypesForRegions(selectedProvider, instanceTypes, selectedRegions) {
      return getDelegate(selectedProvider).getAvailableTypesForRegions(instanceTypes, selectedRegions);
    }

    function getCategoryForInstanceType(selectedProvider, instanceType) {
      return getCategories(selectedProvider).then(function(categories) {
        var query = {families: [ {instanceTypes: [ {name:instanceType } ] } ] };
        var result = _.result(_.findWhere(categories, query), 'type');
        return result || 'custom';
      });
    }

    return {
      getCategories: getCategories,
      getAvailableTypesForRegions: getAvailableTypesForRegions,
      getAllTypesByRegion: getAllTypesByRegion,
      getCategoryForInstanceType: getCategoryForInstanceType
    };
  }
)
.name;
