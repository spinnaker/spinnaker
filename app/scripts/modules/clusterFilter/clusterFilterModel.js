'use strict';

angular
  .module('cluster.filter.model', ['ui.router'])
  .factory('ClusterFilterModel', function($rootScope, $stateParams) {

    var sortFilter = {};

    function convertParamsToObject(paramList) {
      if(paramList) {
        return _.reduce(paramList.split(','), function (acc, value) {
          acc[value] = true;
          return acc;
        }, {});
      }
    }

    function setSelectedAccounts() {
      return convertParamsToObject($stateParams.acct);
    }

    function setSelectedRegions() {
      return convertParamsToObject($stateParams.reg);
    }

    function setStatus() {
      return convertParamsToObject($stateParams.status);
    }

    function setProviderType() {
      return convertParamsToObject($stateParams.providerType);
    }

    function setInstanceType() {
      return convertParamsToObject($stateParams.instanceType);
    }

    function clearSideFilters() {
      sortFilter.account = undefined;
      sortFilter.region = undefined;
      sortFilter.status = undefined;
      sortFilter.providerType = undefined;
      sortFilter.instanceType = undefined;
      sortFilter.filter = '';
    }

    function activate() {
      var defPrimary = 'account', defSecondary = 'region';

      sortFilter.allowSorting = true;
      sortFilter.sortPrimary = $stateParams.primary || defPrimary;
      sortFilter.sortSecondary = $stateParams.secondary || defSecondary;
      sortFilter.filter = $stateParams.q || '';
      sortFilter.showAllInstances = ($stateParams.hideInstances ? false : true);
      sortFilter.hideHealthy = ($stateParams.hideHealthy === 'true' || $stateParams.hideHealthy === true);
      sortFilter.hideDisabled = ($stateParams.hideDisabled === 'true' || $stateParams.hideDisabled === true);
      sortFilter.account = setSelectedAccounts();
      sortFilter.region = setSelectedRegions();
      sortFilter.status = setStatus();
      sortFilter.providerType = setProviderType();
      sortFilter.instanceType = setInstanceType();

      sortFilter.sortOptions = [
        { label: 'Account', key: 'account' },
        { label: 'Cluster Name', key: 'cluster' },
        { label: 'Region', key: 'region' }
      ];
    }

    // this resets the singleton on a state change
    $rootScope.$on('$stateChangeSuccess', activate);

    activate();

    return {
      activate: activate,
      clearFilters: clearSideFilters,
      sortFilter: sortFilter,
      groups: [],
      displayOptions: {}
    };

  });

