'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.kubernetes.search.formatter', [])
  .factory('kubernetesSearchResultFormatter', [
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
