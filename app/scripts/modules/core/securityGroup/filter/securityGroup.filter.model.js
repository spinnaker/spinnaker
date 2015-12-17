'use strict';

let angular = require('angular');

module.exports = angular
  .module('securityGroup.filter.model', [
    require('../../filterModel/filter.model.service.js'),
    require('../../navigation/urlParser.service.js'),
  ])
  .factory('SecurityGroupFilterModel', function($rootScope, filterModelService, urlParser) {

    var filterModel = this;
    var mostRecentParams = null;

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
      return isSecurityGroupState(stateName) || isChildState(stateName);
    }

    function isChildState(stateName) {
      return stateName.indexOf('securityGroups.') > -1;
    }

    function movingToSecurityGroupState(toState) {
      return isSecurityGroupStateOrChild(toState.name);
    }

    function movingFromSecurityGroupState (toState, fromState) {
      return isSecurityGroupStateOrChild(fromState.name) && !isSecurityGroupStateOrChild(toState.name);
    }

    function shouldRouteToSavedState(toParams, fromState) {
      return filterModel.hasSavedState(toParams) && !isSecurityGroupStateOrChild(fromState.name);
    }

    function fromSecurityGroupsState(fromState) {
      return fromState.name.indexOf('home.applications.application.insight') === 0 &&
        fromState.name.indexOf('home.applications.application.insight.securityGroups') === -1;
    }

    // WHY??? Because, when the stateChangeStart event fires, the $location.search() will return whatever the query
    // params are on the route we are going to, so if the user is using the back button, for example, to go to the
    // Infrastructure page with a search already entered, we'll pick up whatever search was entered there, and if we
    // come back to this application view, we'll get whatever that search was.
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
      if (movingFromSecurityGroupState(toState, fromState)) {
        filterModel.saveState(fromState, fromParams, mostRecentParams);
      }
    });

    $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams, fromState) {
      if (isSecurityGroupStateOrChild(toState.name) && isSecurityGroupStateOrChild(fromState.name)) {
        filterModel.applyParamsToUrl();
        return;
      }
      if (movingToSecurityGroupState(toState)) {
        if (shouldRouteToSavedState(toParams, fromState)) {
          filterModel.restoreState(toParams);
        }
        if (fromSecurityGroupsState(fromState) && !filterModel.hasSavedState(toParams)) {
          filterModel.clearFilters();
        }
      }
    });

    filterModel.activate();

    return this;

  });
