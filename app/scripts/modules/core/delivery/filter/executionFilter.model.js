'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.delivery.filter.executionFilter.model', [
    require('../../filterModel/filter.model.service.js'),
    require('../../navigation/urlParser.service.js'),
    require('../../cache/viewStateCache.js')
  ])
  .factory('ExecutionFilterModel', function($rootScope, filterModelService, urlParser, viewStateCache) {

    var filterModel = this;
    var mostRecentParams = null;

    // Store count globally for 180 days
    var configViewStateCache = viewStateCache.createCache('executionFilters', {
      version: 1,
      maxAge: 180 * 24 * 60 * 60 * 1000,
    });

    function getCachedViewState() {
      let cached = configViewStateCache.get('#global') || {},
          defaults = { count: 2, groupBy: 'name' };
      return angular.extend(defaults, cached);
    }

    var groupCount = getCachedViewState().count;
    var groupBy = getCachedViewState().groupBy;

    this.mostRecentApplication = null;

    var filterModelConfig = [
      { model: 'filter', param: 'q', clearValue: '', type: 'string', filterLabel: 'search', },
      { model: 'pipeline', param: 'pipeline', type: 'object', },
      { model: 'status', type: 'object', },
    ];

    filterModelService.configureFilterModel(this, filterModelConfig);

    function cacheConfigViewState() {
      configViewStateCache.put('#global', { count: groupCount, groupBy: groupBy });
    }

    // A nice way to avoid watches is to define a property on an object
    Object.defineProperty(this.sortFilter, 'count', {
      get: function() {
        return groupCount;
      },
      set: function(count) {
        groupCount = count;
        cacheConfigViewState();
      }
    });

    Object.defineProperty(this.sortFilter, 'groupBy', {
      get: function() {
        return groupBy;
      },
      set: function(grouping) {
        groupBy = grouping;
        cacheConfigViewState();
      }
    });

    function isExecutionState(stateName) {
      return stateName === 'home.applications.application.executions' ||
        stateName === 'home.project.application.executions';
    }

    function isExecutionStateOrChild(stateName) {
      return isExecutionState(stateName) || isChildState(stateName);
    }

    function isChildState(stateName) {
      return stateName.indexOf('executions.execution') > -1;
    }

    function movingToExecutionsState(toState) {
      return isExecutionStateOrChild(toState.name);
    }

    function movingFromExecutionsState (toState, fromState) {
      return isExecutionStateOrChild(fromState.name) && !isExecutionStateOrChild(toState.name);
    }

    function fromApplicationListState(fromState) {
      return fromState.name === 'home.applications';
    }

    function shouldRouteToSavedState(toParams, fromState) {
      return filterModel.hasSavedState(toParams) && !isExecutionStateOrChild(fromState.name);
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
      if (movingFromExecutionsState(toState, fromState)) {
        filterModel.saveState(fromState, fromParams, mostRecentParams);
      }
    });

    $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams, fromState) {
      if (movingToExecutionsState(toState) && isExecutionStateOrChild(fromState.name)) {
        filterModel.applyParamsToUrl();
        return;
      }
      if (movingToExecutionsState(toState)) {
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
