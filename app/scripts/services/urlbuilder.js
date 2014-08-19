'use strict';

var angular = require('angular');

angular.module('deckApp')
  .factory('urlBuilder', function() {
    var getClusters = function(input) {
      return ['#',
        'applications',
        input.application,
        'clusters'].join('/');
    };

    var getCluster = function(input) {
      return [getClusters(input),
        input.account,
        input.cluster].join('/');
    };

    var lookup = {
      'serverGroups': function(input) {
        return [
          getCluster(input),
          'serverGroupDetails'
        ].join('/')+'?serverGroup='+input.serverGroup+'&account='+input.account+'&region='+input.region;
      },
      'serverGroupInstances': function(input) {
        return [
          getCluster(input),
          'instanceDetails'
        ].join('/')+'?instanceId='+input.instance;
      },
      'clusters': function(input) {
        return getCluster(input);
      },
      'applications': function(input) {
        return getClusters(input);
      },
    };

    return function(input) {
      var builder = lookup[input.type];
      if (angular.isDefined(builder)) {
        return builder(input);
      } else {
        return '/';
      }
    };

  });
