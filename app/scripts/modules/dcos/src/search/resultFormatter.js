'use strict';

const angular = require('angular');

export const DCOS_SEARCH_RESULTFORMATTER = 'spinnaker.dcos.search.formatter';
export const name = DCOS_SEARCH_RESULTFORMATTER; // for backwards compatibility
angular.module(DCOS_SEARCH_RESULTFORMATTER, []).factory('dcosSearchResultFormatter', [
  '$q',
  function ($q) {
    return {
      instances: function (entry) {
        return $q.when((entry.name || entry.instanceId) + ' (' + entry.namespace + ')');
      },
      serverGroups: function (entry) {
        return $q.when((entry.name || entry.serverGroup) + ' (' + (entry.namespace || entry.region) + ')');
      },
      loadBalancers: function (entry) {
        return $q.when(entry.name + ' (' + (entry.namespace || entry.region) + ')');
      },
    };
  },
]);
