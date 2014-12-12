'use strict';


angular.module('deckApp')
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

    return {
      getCategories: getCategories,
      getAvailableTypesForRegions: getAvailableTypesForRegions,
      getAllTypesByRegion: getAllTypesByRegion
    };
  }
);
