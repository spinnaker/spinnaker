'use strict';

angular
  .module('cluster.filter.model', [])
  .service('clusterFilterModel', function($stateParams) {
    var defPrimary = 'account', defSecondary = 'region';
    var sortFilter = {};

    sortFilter.allowSorting = true;
    sortFilter.sortPrimary = $stateParams.primary || defPrimary;
    sortFilter.sortSecondary = $stateParams.secondary || defSecondary;
    sortFilter.filter = $stateParams.q || '';
    sortFilter.showAllInstances = ($stateParams.hideInstances ? false : true);
    sortFilter.hideHealthy = ($stateParams.hideHealthy === 'true' || $stateParams.hideHealthy === true);
    sortFilter.hideDisabled = ($stateParams.hideDisabled === 'true' || $stateParams.hideDisabled === true);

    return sortFilter;
  });

