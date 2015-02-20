'use strict';

angular
  .module('cluster.filter.model', ['ui.router', 'deckApp.utils.lodash'])
  .factory('ClusterFilterModel', function($rootScope, $state, $stateParams, _) {

    var sortFilter = {
      instanceSort: { key: 'launchTime' }
    };

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

    function setZone() {
      return convertParamsToObject($stateParams.zone);
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
        instanceType: undefined,
        zone: undefined,
      });
    }

    function setParams(params) {
      clearFilterParams(params);
      _.defaults(params, {
        q: angular.copy(sortFilter.filter),
        acct: convertObjectToParam(sortFilter.account),
        reg: convertObjectToParam(sortFilter.region),
        status: convertObjectToParam(sortFilter.status),
        instanceType: convertObjectToParam(sortFilter.instanceType),
        zone: convertObjectToParam(sortFilter.availabilityZone),
        instanceSort: sortFilter.instanceSort.key,
      });

      return params;
    }


    function activate() {
      var defPrimary = 'account', defSecondary = 'cluster';

      sortFilter.sortPrimary = $stateParams.primary || defPrimary;
      sortFilter.sortSecondary = $stateParams.secondary || defSecondary;
      sortFilter.filter = $stateParams.q || '';
      sortFilter.showAllInstances = ($stateParams.hideInstances ? false : true);
      sortFilter.listInstances = ($stateParams.listInstances ? true : false);
      sortFilter.hideHealthy = ($stateParams.hideHealthy === 'true' || $stateParams.hideHealthy === true);
      sortFilter.hideDisabled = ($stateParams.hideDisabled === 'true' || $stateParams.hideDisabled === true);
      sortFilter.account = setSelectedAccounts();
      sortFilter.region = setSelectedRegions();
      sortFilter.status = setStatus();
      sortFilter.providerType = setProviderType();
      sortFilter.instanceType = setInstanceType();
      sortFilter.availabilityZone = setZone();
      sortFilter.instanceSort.key = $stateParams.instanceSort || 'launchTime';

      sortFilter.sortOptions = [
        { label: 'Account', key: 'account' },
        { label: 'Cluster Name', key: 'cluster' },
        { label: 'Region', key: 'region' }
      ];
    }

    function isClusterState(stateName) {
      return stateName === 'home.applications.application.insight.clusters' || stateName.indexOf('clusters.') > -1;
    }

    function movingToClusterState(toState) {
      return isClusterState(toState.name);
    }

    function hasSavedClusterState(toParams) {
      return savedClusterState[toParams.application] !== undefined && savedClusterStateParams[toParams.application] !== undefined;
    }

    function routeToSavedState(event, toParams) {
      event.preventDefault();
      $state.go(savedClusterState[toParams.application], savedClusterStateParams[toParams.application], {reload: false});
    }

    function movingFromClusterState (toState, fromState) {
      return isClusterState(fromState.name) && !isClusterState(toState.name);
    }

    function saveClusterState(fromState, fromParams) {
      if(fromParams.application) {
        savedClusterState[fromParams.application] = fromState;
        savedClusterStateParams[fromParams.application] = fromParams;
      }
    }

    function shouldRouteToSavedState(toState, toParams, fromState) {
      var applicationCurrentSavedState = savedClusterState[toParams.application];
      return toState.name !== applicationCurrentSavedState.name && !isClusterState(fromState.name);
    }

    function fromApplicationListState(fromState) {
      return fromState.name === 'home.applications.application';
    }

    var savedClusterState = {};
    var savedClusterStateParams = {};

    $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {

      if(movingFromClusterState(toState, fromState)) {
        setParams(fromParams);
        saveClusterState(fromState, fromParams);
      }

      if(movingToClusterState(toState)) {
        if (hasSavedClusterState(toParams) && shouldRouteToSavedState(toState, toParams, fromState)) {
          routeToSavedState(event, toParams);
        }

        if(fromApplicationListState(fromState) && hasSavedClusterState(toParams)) {
          angular.copy(savedClusterStateParams[toParams.application], $stateParams);
          activate();
        }

        if(fromApplicationListState(fromState) && !hasSavedClusterState(toParams)) {
          clearSideFilters();
        }

        if(isClusterState(toState.name) && isClusterState(fromState.name)) {
          setParams(toParams);
        }

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

