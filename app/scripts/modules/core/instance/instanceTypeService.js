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

    function getInstanceTypeDetails(selectedProvider, instanceType) {
      return getCategories(selectedProvider).then(function(categories) {
        var query = {families: [ {instanceTypes: [ {name:instanceType } ] } ] };
        var category = _.findWhere(categories, query);
        var instanceTypeDetails;

        if (category && category.families && category.families.length && category.families[0].instanceTypes) {
          instanceTypeDetails = _.find(category.families[0].instanceTypes, candidateInstanceType => {
            return candidateInstanceType.name === instanceType;
          });
        }

        return instanceTypeDetails || {};
      });
    }

    return {
      getCategories: getCategories,
      getAvailableTypesForRegions: getAvailableTypesForRegions,
      getAllTypesByRegion: getAllTypesByRegion,
      getCategoryForInstanceType: getCategoryForInstanceType,
      getInstanceTypeDetails: getInstanceTypeDetails
    };
  }
);
