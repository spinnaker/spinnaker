'use strict';

let angular = require('angular');

module.exports = angular
  .module('loadBalancer.filter.model', [
    require('../../filterModel/filter.model.service.js'),
  ])
  .factory('LoadBalancerFilterModel', function($rootScope, filterModelService) {

    var filterModel = this;

    var filterModelConfig = [
      { model: 'filter', param: 'q', clearValue: '', type: 'string', filterLabel: 'search' },
      { model: 'account', param: 'acct', type: 'object' },
      { model: 'region', param: 'reg', type: 'object' },
      { model: 'stack', param: 'stack', type: 'object', },
      { model: 'status', type: 'object', filterTranslator: {Up: 'Healthy', Down: 'Unhealthy', OutOfService: 'Out of Service'} },
      { model: 'availabilityZone', param: 'zone', type: 'object', filterLabel: 'availability zone' },
      { model: 'providerType', type: 'object', filterLabel: 'provider' },
      { model: 'showInstances', displayOption: true, type: 'boolean' },
      { model: 'showServerGroups', param: 'hideServerGroups', inverse: true, displayOption: true, type: 'inverse-boolean' },
    ];

    filterModelService.configureFilterModel(filterModel, filterModelConfig);

    function isLoadBalancerState(stateName) {
      return stateName === 'home.applications.application.insight.loadBalancers';
    }

    function isLoadBalancerStateOrChild(stateName) {
      return isLoadBalancerState(stateName) || stateName.indexOf('loadBalancers.') > -1;
    }

    function movingToLoadBalancerState(toState) {
      return isLoadBalancerStateOrChild(toState.name);
    }

    function movingFromLoadBalancerState (toState, fromState) {
      return isLoadBalancerStateOrChild(fromState.name) && !isLoadBalancerStateOrChild(toState.name);
    }

    function shouldRouteToSavedState(toState, toParams, fromState) {
      return isLoadBalancerState(toState.name) &&
        filterModel.hasSavedState(toParams) &&
        !isLoadBalancerStateOrChild(fromState.name);
    }

    function fromLoadBalancersState(fromState) {
      return fromState.name.indexOf('home.applications.application.insight') === 0 &&
        fromState.name.indexOf('home.applications.application.insight.loadBalancers') === -1;
    }

    $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {
      if (movingFromLoadBalancerState(toState, fromState)) {
        filterModel.saveState(fromState, fromParams);
      }

      if (movingToLoadBalancerState(toState)) {
        if (filterModel.hasSavedState(toParams) && shouldRouteToSavedState(toState, toParams, fromState)) {
          filterModel.restoreState(toParams);
        }

        if (fromLoadBalancersState(fromState) && !filterModel.hasSavedState(toParams)) {
          filterModel.clearFilters();
        }
      }
    });

    filterModel.activate();

    return this;

  }).name;
