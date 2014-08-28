'use strict';

var angular = require('angular');

angular.module('deckApp')
  .factory('urlBuilder', function($state) {
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
        ].join('/')+'?serverGroup='+input.serverGroup+'&accountId='+input.account+'&region='+input.region;
      },
      'serverGroupInstances': function(input) {
        return [
          getCluster(input),
          'instanceDetails'
        ].join('/')+'?instanceId='+input.instanceId;
      },
      'clusters': function(input) {
        return getCluster(input);
      },
      'applications': function(input) {
        return getClusters(input);
      },
      'loadBalancerServerGroups': function(input) {
        return $state.href(
          'home.applications.application.insight.loadBalancers.loadBalancerDetails',
          {
            application: input.application,
            name: input.loadBalancer,
            region: input.region,
            accountId: input.account
          }
        );
      }
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
