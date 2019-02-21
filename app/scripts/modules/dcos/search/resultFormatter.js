'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.dcos.search.formatter', [])
  .factory('dcosSearchResultFormatter', ['$q', function($q) {
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
  }]);
