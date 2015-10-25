'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.delivery.filter.executionFilter.model', [
    require('../../filterModel/filter.model.service.js'),
    require('../../navigation/urlParser.service.js'),
  ])
  .factory('ExecutionFilterModel', function($rootScope, filterModelService, urlParser) {

    var filterModel = this;
    var mostRecentParams = null;

    this.mostRecentApplication = null;

    var filterModelConfig = [
      { model: 'filter', param: 'q', clearValue: '', type: 'string', filterLabel: 'search', },
      { model: 'pipeline', param: 'pipeline', type: 'object', },
      { model: 'status', type: 'object', },
      { model: 'groupBy', displayOption: true, type: 'string', defaultValue: 'name', },
      //{ model: 'count', displayOption: true, type: 'number', defaultValue: 2 },
    ];

    filterModelService.configureFilterModel(this, filterModelConfig);
    this.sortFilter.count = 10; // TODO: remove, uncomment count config above if we ever have the power to show multiple executions

    function isExecutionState(stateName) {
      return stateName === 'home.applications.application.insight.executions' ||
        stateName === 'home.project.application.insight.executions';
    }

    function isExecutionStateOrChild(stateName) {
      return isExecutionState(stateName) || isChildState(stateName);
    }

    function isChildState(stateName) {
      return stateName.indexOf('executions.execution') > -1;
    }

    function movingToClusterState(toState) {
      return isExecutionStateOrChild(toState.name);
    }

    function movingFromClusterState (toState, fromState) {
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
      if (movingFromClusterState(toState, fromState)) {
        filterModel.saveState(fromState, fromParams, mostRecentParams);
      }
    });

    $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams, fromState) {
      if (isExecutionStateOrChild(toState.name)) {
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

  }).name;
