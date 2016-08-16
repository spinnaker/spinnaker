'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.serverGroup.serverGroup.directive', [
  require('../cluster/filter/clusterFilter.service'),
  require('../cluster/filter/clusterFilter.model'),
  require('../cluster/filter/multiselect.model'),
  require('../instance/instances.directive'),
  require('../instance/instanceList.directive'),
  require('./serverGroup.transformer'),
])
  .directive('serverGroup', function ($rootScope, $timeout, $filter, _, clusterFilterService,
                                      MultiselectModel, ClusterFilterModel, serverGroupTransformer) {
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
      link: function (scope) {

        let lastStringVal = null;
        scope.sortFilter = ClusterFilterModel.sortFilter;
        scope.multiselectModel = MultiselectModel;

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

          if (serverGroup.buildInfo && serverGroup.buildInfo.jenkins &&
              (serverGroup.buildInfo.jenkins.host || serverGroup.buildInfo.jenkins.fullUrl || serverGroup.buildInfo.buildInfoUrl)) {
            var jenkins = serverGroup.buildInfo.jenkins;

            viewModel.jenkins = {
              number: jenkins.number
            };

            if (serverGroup.buildInfo.jenkins.host) {
              viewModel.jenkins.href = [jenkins.host + 'job', jenkins.name, jenkins.number, ''].join('/');
            }
            if (serverGroup.buildInfo.jenkins.fullUrl) {
              viewModel.jenkins.href = serverGroup.buildInfo.jenkins.fullUrl;
            }
            if (serverGroup.buildInfo.buildInfoUrl) {
              viewModel.jenkins.href = serverGroup.buildInfo.buildInfoUrl;
            }
          } else if (_.has(serverGroup, 'buildInfo.images')) {
            viewModel.images = serverGroup.buildInfo.images;
          }

          let modelStringVal = JSON.stringify(viewModel, serverGroupTransformer.jsonReplacer);

          if (lastStringVal !== modelStringVal) {
            scope.viewModel = viewModel;
            lastStringVal = modelStringVal;
          }

          viewModel.serverGroup.runningTasks = serverGroup.runningTasks;
          viewModel.serverGroup.executions = serverGroup.executions;

        }

        scope.loadDetails = function(event) {
          $timeout(() => {
            if (event.isDefaultPrevented() || (event.originalEvent && (event.originalEvent.defaultPrevented || event.originalEvent.target.href))) {
              return;
            }
            MultiselectModel.toggleServerGroup(scope.serverGroup);
            event.preventDefault();
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
});
