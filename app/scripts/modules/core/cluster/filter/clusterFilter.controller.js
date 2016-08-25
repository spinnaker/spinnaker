'use strict';

let angular = require('angular');

// controllerAs: clustersFilters

module.exports = angular.module('cluster', [
  require('./collapsibleFilterSection.directive.js'),
  require('./clusterFilter.service.js'),
  require('./clusterFilter.model.js'),
  require('../../utils/lodash.js'),
  require('../../filterModel/dependentFilter/dependentFilter.service.js'),
  require('./clusterDependentFilterHelper.service.js')
])
  .controller('ClusterFilterCtrl', function ($scope, app, _, $log, clusterFilterService,
                                             ClusterFilterModel, $rootScope,
                                             clusterDependentFilterHelper, dependentFilterService) {

    $scope.application = app;
    $scope.sortFilter = ClusterFilterModel.sortFilter;

    var ctrl = this;

    this.updateClusterGroups = () => {
      let { providerType, instanceType, account, availabilityZone, region } = dependentFilterService.digestDependentFilters({
        sortFilter: ClusterFilterModel.sortFilter,
        dependencyOrder: ['providerType', 'account', 'region', 'availabilityZone', 'instanceType'],
        pool: clusterDependentFilterHelper.poolBuilder(app.serverGroups.data)
      });

      ctrl.providerTypeHeadings = providerType;
      ctrl.accountHeadings = account;
      ctrl.availabilityZoneHeadings = availabilityZone;
      ctrl.regionHeadings = region;
      ctrl.instanceTypeHeadings = instanceType;

      ClusterFilterModel.applyParamsToUrl();
      clusterFilterService.updateClusterGroups(app);
    };

    function getHeadingsForOption(option) {
      return _.compact(_.uniq(_.pluck(app.serverGroups.data, option))).sort();
    }

    function clearFilters() {
      clusterFilterService.clearFilters();
      clusterFilterService.updateClusterGroups(app);
      ctrl.updateClusterGroups();
    }

    this.initialize = function() {
      ctrl.stackHeadings = ['(none)'].concat(getHeadingsForOption('stack'));
      ctrl.categoryHeadings = getHeadingsForOption('category');
      ctrl.clearFilters = clearFilters;
      $scope.clusters = app.clusters;
      ctrl.updateClusterGroups();
    };


    if (app.serverGroups.loaded) {
      this.initialize();
    }

    app.serverGroups.onRefresh($scope, this.initialize);

    $scope.$on('$destroy', $rootScope.$on('$locationChangeSuccess', () => {
      ClusterFilterModel.activate();
      clusterFilterService.updateClusterGroups(app);
    }));
  }
);
