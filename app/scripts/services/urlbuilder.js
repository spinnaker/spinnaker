'use strict';

var angular = require('angular');

angular.module('deckApp')
  .factory('urlBuilder', function($state) {
    var lookup = {
      // url for a single serverGroup
      'serverGroups': function(input) {
        return $state.href(
          'home.applications.application.insight.clusters.cluster.serverGroup',
          {
            application: input.application,
            cluster: input.cluster,
            account: input.account,
            accountId: input.account,
            region: input.region,
            serverGroup: input.serverGroup,
          }
        );
      },
      // url for a single instance
      'serverGroupInstances': function(input) {
        return $state.href(
          'home.applications.application.insight.clusters.cluster.instanceDetails',
          {
            application: input.application,
            cluster: input.cluster,
            accountId: input.account,
            instanceId: input.instanceId,
            account: input.account,
          }
        );
      },
      // url for a single cluster
      'clusters': function(input) {
        return $state.href(
          'home.applications.application.insight.clusters.cluster',
          {
            application: input.application,
            cluster: input.cluster,
            account: input.account,
          }
        );
      },
      // url for a single application
      'applications': function(input) {
        return $state.href(
          'home.applications.application.insight.clusters',
          {
            application: input.application,
          }
        );
      },
      // url for a single load balancer
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
