'use strict';

angular
  .module('cluster.filter.model', ['ui.router', 'deckApp.utils.lodash'])
  .factory('ClusterFilterModel', function($rootScope, $state, $stateParams, _) {

    var sortFilter = {};

    function convertParamsToObject(paramList) {
      if(paramList) {
        return _.reduce(paramList.split(','), function (acc, value) {
          acc[value] = true;
          return acc;
        }, {});
      }
    }

    function convertObjectToParam(obj) {
      if(obj) {
        return _.chain(obj)
          .collect(function(val,key) {
             if (val){ return key; }
          })
          .remove(undefined)
          .value()
          .join(',');
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

    function clearFilterParams(params) {
      return _.assign(params, {
        q: undefined,
        acct: undefined,
        reg: undefined,
        status: undefined,
        instanceType: undefined
      });
    }

    function setParams(params) {
      clearFilterParams(params);
      _.defaults(params, {
        q: sortFilter.filter,
        acct: convertObjectToParam(sortFilter.account),
        reg: convertObjectToParam(sortFilter.region),
        status: convertObjectToParam(sortFilter.status),
        instanceType: convertObjectToParam(sortFilter.instanceType)
      });

      return params;
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

    function fromOtherTabToClusterTab(fromState, toState) {
      return fromState.name.indexOf('clusters') === -1  &&
             toState.name.indexOf('clusters') > -1;
    }

    function fromClusterTabToOtherTab(fromState, toState) {
      return fromState.name.indexOf('clusters') > -1  &&
        toState.name.indexOf('clusters') === -1;
    }

    function notFromApplicationsList(fromState) {
      return fromState.name !== 'home.applications.application';
    }

    function notToApplicationList(toState) {
      return toState.name !== 'home.applications';
    }

    var savedClusterState;
    var savedClusterParams;

    $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {
      if(fromOtherTabToClusterTab(fromState, toState) && notFromApplicationsList(fromState)) {
        if(savedClusterState && savedClusterParams && toState.name !== savedClusterState.name) {
          event.preventDefault();
          $state.go(savedClusterState.name, savedClusterParams, {reload: true});
        }
      }

      if(fromClusterTabToOtherTab(fromState, toState) && notToApplicationList(toState)) {
        savedClusterState = fromState;
        savedClusterParams = fromParams;
      } else {
        savedClusterState = undefined;
        savedClusterParams = undefined;
      }

      if(toState.name.indexOf('clusters') > -1) {
        setParams(toParams);
      }
    });

    activate();

    return {
      activate: activate,
      clearFilters: clearSideFilters,
      sortFilter: sortFilter,
      groups: [],
      displayOptions: {},
      setParams: setParams,
      convertObjectToParam: convertObjectToParam

    };

  });

