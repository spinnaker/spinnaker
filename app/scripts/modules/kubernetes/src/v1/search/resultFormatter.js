'use strict';

import { module } from 'angular';

export const KUBERNETES_V1_SEARCH_RESULTFORMATTER = 'spinnaker.kubernetes.search.formatter';
export const name = KUBERNETES_V1_SEARCH_RESULTFORMATTER; // for backwards compatibility
module(KUBERNETES_V1_SEARCH_RESULTFORMATTER, []).factory('kubernetesSearchResultFormatter', [
  '$q',
  function($q) {
    return {
      instances: function(entry) {
        return $q.when((entry.name || entry.instanceId) + ' (' + entry.namespace + ')');
      },
      serverGroups: function(entry) {
        return $q.when((entry.name || entry.serverGroup) + ' (' + (entry.namespace || entry.region) + ')');
      },
      loadBalancers: function(entry) {
        return $q.when(entry.name + ' (' + (entry.namespace || entry.region) + ')');
      },
    };
  },
]);
