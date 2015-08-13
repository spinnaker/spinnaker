'use strict';

let angular = require('angular');

module.exports = angular
  .module('cluster.filter.model', [
    require('../filterModel/filter.model.service.js')
  ])
  .factory('ClusterFilterModel', function($rootScope, filterModelService) {

    var filterModel = this;

    var filterModelConfig = [
      { model: 'filter', param: 'q', clearValue: '', type: 'string', filterLabel: 'search', },
      { model: 'account', param: 'acct', type: 'object', },
      { model: 'region', param: 'reg', type: 'object', },
      { model: 'status', type: 'object', filterTranslator: {Up: 'Healthy', Down: 'Unhealthy', OutOfService: 'Out of Service'}},
      { model: 'availabilityZone', param: 'zone', type: 'object', filterLabel: 'availability zone' },
      { model: 'instanceType', type: 'object', filterLabel: 'instance type'},
      { model: 'providerType', type: 'object', filterLabel: 'provider', },
      { model: 'minInstances', type: 'number', filterLabel: 'instance count (min)', },
      { model: 'maxInstances', type: 'number', filterLabel: 'instance count (max)', },
      { model: 'showAllInstances', param: 'hideInstances', displayOption: true, type: 'inverse-boolean', },
      { model: 'listInstances', displayOption: true, type: 'boolean', },
      { model: 'instanceSort', displayOption: true, type: 'sortKey', defaultValue: 'launchTime' },
    ];

    filterModelService.configureFilterModel(this, filterModelConfig);

    function isClusterState(stateName) {
      return stateName === 'home.applications.application.insight.clusters';
    }

    function isClusterStateOrChild(stateName) {
      return isClusterState(stateName) || stateName.indexOf('clusters.') > -1;
    }

    function movingToClusterState(toState) {
      return isClusterStateOrChild(toState.name);
    }

    function movingFromClusterState (toState, fromState) {
      return isClusterStateOrChild(fromState.name) && !isClusterStateOrChild(toState.name);
    }

    function fromApplicationListState(fromState) {
      return fromState.name === 'home.applications';
    }

    function shouldRouteToSavedState(toState, toParams, fromState) {
      return isClusterState(toState.name) &&
        filterModel.hasSavedState(toParams) &&
        !isClusterStateOrChild(fromState.name);
    }

    $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {
      if (movingFromClusterState(toState, fromState)) {
        filterModel.saveState(fromState, fromParams);
      }
      if (movingToClusterState(toState)) {
        if (filterModel.hasSavedState(toParams) && shouldRouteToSavedState(toState, toParams, fromState)) {
          filterModel.restoreState(toParams);
        }
        if (fromApplicationListState(fromState) && !filterModel.hasSavedState(toParams)) {
          filterModel.clearFilters();
        }
      }
    });

    filterModel.activate();

    return this;

  }).name;
