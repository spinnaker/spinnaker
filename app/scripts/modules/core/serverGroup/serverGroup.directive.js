'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.serverGroup.serverGroup.directive', [
  require('../cluster/filter/clusterFilter.service.js'),
  require('../cluster/filter/clusterFilter.model.js'),
  require('../instance/instances.directive.js'),
  require('../instance/instanceList.directive.js'),
  require('./serverGroup.transformer.js'),
])
  .directive('serverGroup', function ($rootScope, $timeout, $filter, _, clusterFilterService, ClusterFilterModel, serverGroupTransformer) {
    return {
      restrict: 'E',
      templateUrl: require('./serverGroup.directive.html'),
      scope: {
        cluster: '=',
        serverGroup: '=',
        application: '=',
        parentHeading: '=',
        hasLoadBalancers: '=',
        hasDiscovery: '=',
      },
      link: function (scope, el) {

        let lastStringVal = null;
        scope.sortFilter = ClusterFilterModel.sortFilter;
        // stolen from uiSref directive
        var base = el.parent().inheritedData('$uiView').state;

        scope.$state = $rootScope.$state;

        function setViewModel() {
          var filteredInstances = scope.serverGroup.instances.filter(clusterFilterService.shouldShowInstance);

          var serverGroup = scope.serverGroup;

          let viewModel = {
            waypoint: [serverGroup.account, serverGroup.region, serverGroup.name].join(':'),
            serverGroup: serverGroup,
            serverGroupSequence: $filter('serverGroupSequence')(serverGroup.name),
            jenkins: null,
            hasBuildInfo: !!serverGroup.buildInfo,
            instances: filteredInstances,
          };

          if (serverGroup.buildInfo && serverGroup.buildInfo.jenkins && serverGroup.buildInfo.jenkins.host) {
            var jenkins = serverGroup.buildInfo.jenkins;
            viewModel.jenkins = {
              href: [jenkins.host + 'job', jenkins.name, jenkins.number, ''].join('/'),
              number: jenkins.number,
            };
          }

          let modelStringVal = JSON.stringify(viewModel, serverGroupTransformer.jsonReplacer);

          if (lastStringVal !== modelStringVal) {
            scope.viewModel = viewModel;
            lastStringVal = modelStringVal;
          }

          viewModel.serverGroup.runningTasks = serverGroup.runningTasks;
          viewModel.serverGroup.executions = serverGroup.executions;

        }

        scope.loadDetails = function(e) {
          $timeout(function() {
            var serverGroup = scope.serverGroup;
            // anything handled by ui-sref or actual links should be ignored
            if (e.isDefaultPrevented() || (e.originalEvent && (e.originalEvent.defaultPrevented || e.originalEvent.target.href))) {
              return;
            }
            var params = {
              region: serverGroup.region,
              accountId: serverGroup.account,
              serverGroup: serverGroup.name,
              provider: serverGroup.type,
            };
            // also stolen from uiSref directive
            scope.$state.go('.serverGroup', params, {relative: base, inherit: true});
          });
        };

        setViewModel();

        scope.headerIsSticky = function() {
          if (!scope.sortFilter.showAllInstances) {
            return false;
          }
          if (scope.sortFilter.listInstances) {
            return scope.viewModel.instances.length > 1;
          }
          return scope.viewModel.instances.length > 20;
        };

        scope.$watch('sortFilter', setViewModel, true);
      }
    };
}).name;
