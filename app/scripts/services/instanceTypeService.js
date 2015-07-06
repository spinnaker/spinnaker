'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.instanceType.service', [
  require('../modules/caches/deckCacheFactory.js'),
  require('utils/lodash.js'),
  require('./awsInstanceTypeService.js'),
  require('./gceInstanceTypeService.js')
])
  .factory('instanceTypeService', function ($http, $q, settings, _, $window, awsInstanceTypeService, gceInstanceTypeService) {

    // TODO: Make the selection of the delegate pluggable? Maybe mort (or something like it) provides this data and we
    // should just pass the selectedProvider argument to that rest service?
    function getDelegate(selectedProvider) {
      return (!selectedProvider || selectedProvider === 'aws') ? awsInstanceTypeService : gceInstanceTypeService;
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
