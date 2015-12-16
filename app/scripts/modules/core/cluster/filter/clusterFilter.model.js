'use strict';

let angular = require('angular');

module.exports = angular
  .module('cluster.filter.model', [
    require('../../filterModel/filter.model.service.js'),
    require('../../navigation/urlParser.service.js'),
  ])
  .factory('ClusterFilterModel', function($rootScope, filterModelService, urlParser) {

    var filterModel = this;
    var mostRecentParams = null;

    var filterModelConfig = [
      { model: 'filter', param: 'q', clearValue: '', type: 'string', filterLabel: 'search', },
      { model: 'account', param: 'acct', type: 'object', },
      { model: 'region', param: 'reg', type: 'object', },
      { model: 'stack', param: 'stack', type: 'object', },
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
      return stateName === 'home.applications.application.insight.clusters' ||
        stateName === 'home.project.application.insight.clusters';
    }

    function isClusterStateOrChild(stateName) {
      return isClusterState(stateName) || isChildState(stateName);
    }

    function isChildState(stateName) {
      return stateName.indexOf('clusters.') > -1;
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

    function shouldRouteToSavedState(toParams, fromState) {
      return filterModel.hasSavedState(toParams) && !isClusterStateOrChild(fromState.name);
    }

    // WHY??? Because, when the stateChangeStart event fires, the $location.search() will return whatever the query
    // params are on the route we are going to, so if the user is using the back button, for example, to go to the
    // Infrastructure page with a search already entered, we'll pick up whatever search was entered there, and if we
    // come back to this application's clusters view, we'll get whatever that search was.
    $rootScope.$on('$locationChangeStart', function(event, toUrl, fromUrl) {
      let [oldBase, oldQuery] = fromUrl.split('?'),
          [newBase, newQuery] = toUrl.split('?');

      if (oldBase === newBase) {
        mostRecentParams = newQuery ? urlParser.parseQueryString(newQuery) : {};
      } else {
        mostRecentParams = oldQuery ? urlParser.parseQueryString(oldQuery) : {};
      }
    });

    $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {
      if (movingFromClusterState(toState, fromState)) {
        filterModel.saveState(fromState, fromParams, mostRecentParams);
      }
    });

    $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams, fromState) {
      if (movingToClusterState(toState) && isClusterStateOrChild(fromState.name)) {
        filterModel.applyParamsToUrl();
        return;
      }
      if (movingToClusterState(toState)) {
        if (shouldRouteToSavedState(toParams, fromState)) {
          filterModel.restoreState(toParams);
        }
        if (fromApplicationListState(fromState) && !filterModel.hasSavedState(toParams)) {
          filterModel.clearFilters();
        }
      }
    });

    filterModel.activate();

    return this;

  });
