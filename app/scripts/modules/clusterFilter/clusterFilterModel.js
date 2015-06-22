'use strict';

let angular = require('angular');

module.exports = angular
  .module('cluster.filter.model', [
    require('angular-ui-router'),
    require('../utils/lodash.js'),
  ])
  .factory('ClusterFilterModel', function($rootScope, $state, $stateParams, $location, _) {

    var sortFilter = {
      instanceSort: { key: 'launchTime' },
      tags: [],
    };

    var savedClusterState = {};
    var savedClusterStateParams = {};
    var savedClusterStateFilters = {};

    function convertParamsToObject(key) {
      var paramList = $location.search()[key];
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
          .collect(function(val, key) {
             if (val){ return key; }
          })
          .remove(undefined)
          .value()
          .join(',');
      }
    }

    function setSelectedAccounts() {
      return convertParamsToObject('acct');
    }

    function setSelectedRegions() {
      return convertParamsToObject('reg');
    }

    function setStatus() {
      return convertParamsToObject('status');
    }

    function setProviderType() {
      return convertParamsToObject('providerType');
    }

    function setInstanceType() {
      return convertParamsToObject('instanceType');
    }

    function setZone() {
      return convertParamsToObject('zone');
    }

    function clearSideFilters() {
      sortFilter.account = undefined;
      sortFilter.region = undefined;
      sortFilter.status = undefined;
      sortFilter.providerType = undefined;
      sortFilter.instanceType = undefined;
      sortFilter.filter = '';
      sortFilter.availabilityZone = undefined;
      sortFilter.minInstances = undefined;
      sortFilter.maxInstances = undefined;
    }

    function clearFilterParams(params) {
      return _.assign(params, {
        q: undefined,
        acct: undefined,
        reg: undefined,
        status: undefined,
        instanceType: undefined,
        zone: undefined,
        minInstances: undefined,
        maxInstances: undefined,
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
        minInstances: sortFilter.minInstances,
        maxInstances: sortFilter.maxInstances,
      });

      return params;
    }


    function activate() {
      var params = $location.search();

      sortFilter.filter = params.q || '';
      sortFilter.showAllInstances = (params.hideInstances ? false : true);
      sortFilter.listInstances = (params.listInstances ? true : false);
      sortFilter.hideHealthy = (params.hideHealthy === 'true' || params.hideHealthy === true);
      sortFilter.hideDisabled = (params.hideDisabled === 'true' || params.hideDisabled === true);
      sortFilter.account = setSelectedAccounts();
      sortFilter.region = setSelectedRegions();
      sortFilter.status = setStatus();
      sortFilter.providerType = setProviderType();
      sortFilter.instanceType = setInstanceType();
      sortFilter.availabilityZone = setZone();
      sortFilter.minInstances = isNaN(params.minInstances) ? undefined : parseInt(params.minInstances);
      sortFilter.maxInstances = isNaN(params.maxInstances) ? undefined : parseInt(params.maxInstances);
      sortFilter.instanceSort.key = params.instanceSort || 'launchTime';

    }

    function isClusterState(stateName) {
      return stateName === 'home.applications.application.insight.clusters';
    }

    function isClusterStateOrChild(stateName) {
      return isClusterState(stateName) || stateName.indexOf('clusters.') > -1;
    }

    function movingToClusterState(toState) {
      return isClusterStateOrChild(toState.name);
    }

    function hasSavedClusterState(toParams) {
      return savedClusterState[toParams.application] !== undefined && savedClusterStateParams[toParams.application] !== undefined;
    }

    function routeToSavedState(event, toParams) {
      event.preventDefault();
      $state.go(savedClusterState[toParams.application], savedClusterStateParams[toParams.application], {reload: false});
    }

    function movingFromClusterState (toState, fromState) {
      return isClusterStateOrChild(fromState.name) && !isClusterStateOrChild(toState.name);
    }

    function saveClusterState(fromState, fromParams) {
      if(fromParams.application) {
        var application = fromParams.application;
        savedClusterState[application] = fromState;
        savedClusterStateParams[application] = fromParams;
        savedClusterStateFilters[application] = $location.search();
      }
    }

    function shouldRouteToSavedState(toState, toParams, fromState) {
      var applicationCurrentSavedState = savedClusterState[toParams.application];
      return isClusterState(toState.name) &&
        toState.name !== applicationCurrentSavedState.name &&
        !isClusterStateOrChild(fromState.name);
    }

    function fromApplicationListState(fromState) {
      return fromState.name === 'home.applications';
    }

    $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {
      if(movingFromClusterState(toState, fromState)) {
        saveClusterState(fromState, fromParams);
        setParams(fromParams);

      }

      if(movingToClusterState(toState)) {
        if (hasSavedClusterState(toParams) && shouldRouteToSavedState(toState, toParams, fromState)) {
          routeToSavedState(event, toParams);
        }

        if(fromApplicationListState(fromState) && hasSavedClusterState(toParams)) {
          angular.copy(savedClusterStateParams[toParams.application], $stateParams);
          $location.search(angular.extend($location.search(), savedClusterStateFilters[toParams.application]));
          activate();
        }

        if(fromApplicationListState(fromState) && !hasSavedClusterState(toParams)) {
          clearSideFilters();
        }

        if(isClusterStateOrChild(toState.name) && isClusterStateOrChild(fromState.name)) {
          if (toParams.application !== fromParams.application) {
            setParams(toParams);
          }
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

