'use strict';

let angular = require('angular');

module.exports = angular
  .module('securityGroup.filter.model', [
    require('../../filterModel/filter.model.service.js'),
  ])
  .factory('SecurityGroupFilterModel', function($rootScope, filterModelService) {

    var filterModel = this;

    var filterModelConfig = [
      { model: 'filter', param: 'q', clearValue: '', type: 'string', filterLabel: 'search' },
      { model: 'account', param: 'acct', type: 'object' },
      { model: 'region', param: 'reg', type: 'object' },
      { model: 'stack', param: 'stack', type: 'object', },
      { model: 'providerType', type: 'object', filterLabel: 'provider' },
      { model: 'showServerGroups', param: 'hideServerGroups', inverse: true, displayOption: true, type: 'inverse-boolean' },
      { model: 'showLoadBalancers', param: 'hideLoadBalancers', inverse: true, displayOption: true, type: 'inverse-boolean' },
    ];

    filterModelService.configureFilterModel(filterModel, filterModelConfig);

    function isSecurityGroupState(stateName) {
      return stateName === 'home.applications.application.insight.securityGroups';
    }

    function isSecurityGroupStateOrChild(stateName) {
      return isSecurityGroupState(stateName) || stateName.indexOf('securityGroups.') > -1;
    }

    function movingToSecurityGroupState(toState) {
      return isSecurityGroupStateOrChild(toState.name);
    }

    function movingFromSecurityGroupState (toState, fromState) {
      return isSecurityGroupStateOrChild(fromState.name) && !isSecurityGroupStateOrChild(toState.name);
    }

    function shouldRouteToSavedState(toState, toParams, fromState) {
      return isSecurityGroupState(toState.name) &&
        filterModel.hasSavedState(toParams) &&
        !isSecurityGroupStateOrChild(fromState.name);
    }

    function fromSecurityGroupsState(fromState) {
      return fromState.name.indexOf('home.applications.application.insight') === 0 &&
        fromState.name.indexOf('home.applications.application.insight.securityGroups') === -1;
    }

    $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {
      if (movingFromSecurityGroupState(toState, fromState)) {
        filterModel.saveState(fromState, fromParams);
      }

      if (movingToSecurityGroupState(toState)) {
        if (filterModel.hasSavedState(toParams) && shouldRouteToSavedState(toState, toParams, fromState)) {
          filterModel.restoreState(toParams);
        }

        if (fromSecurityGroupsState(fromState) && !filterModel.hasSavedState(toParams)) {
          filterModel.clearFilters();
        }
      }
    });

    filterModel.activate();

    return this;

  }).name;
