'use strict';

let angular = require('angular');
import {URL_BUILDER_SERVICE} from 'core/navigation/urlBuilder.service';

module.exports = angular.module('spinnaker.core.cluster.pod.directive', [
  require('./serverGroup.sequence.filter.js'),
  URL_BUILDER_SERVICE,
  require('../serverGroup/serverGroup.directive.js'),
])
  .directive('clusterPod', function(urlBuilderService) {
    return {
      restrict: 'E',
      scope: {
        grouping: '=',
        sortFilter: '=',
        application: '=',
        parentHeading: '=',
      },
      templateUrl: require('./clusterPod.html'),
      link: function(scope) {
        // using location.host here b/c it provides the port, $location.host() does not.
        // Easy way to get this to work in both dev(where we have a port) and prod(where we do not).
        scope.host = location.host;
        scope.permalink = urlBuilderService.buildFromMetadata(
          {
            type: 'clusters',
            application: scope.application.name,
            cluster: scope.grouping.heading,
            account: scope.parentHeading,
          }
        );
      }
    };
  });
