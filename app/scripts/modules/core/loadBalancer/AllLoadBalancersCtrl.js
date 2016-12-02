'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.loadBalancer.controller', [
  require('angular-ui-bootstrap'),
  require('../cloudProvider/providerSelection/providerSelection.service.js'),
  require('./filter/loadBalancer.filter.service.js'),
  require('./filter/loadBalancer.filter.model.js'),
  require('../filterModel/filter.tags.directive.js'),
  require('../cloudProvider/cloudProvider.registry.js'),
])
  .controller('AllLoadBalancersCtrl', function($scope, $uibModal, $timeout,
                                               providerSelectionService, cloudProviderRegistry,
                                               LoadBalancerFilterModel, loadBalancerFilterService, app ) {

    LoadBalancerFilterModel.activate();
    this.initialized = false;

    $scope.application = app;

    $scope.sortFilter = LoadBalancerFilterModel.sortFilter;

    this.groupingsTemplate = require('./groupings.html');

    let updateLoadBalancerGroups = () => {
      LoadBalancerFilterModel.applyParamsToUrl();
      $scope.$evalAsync(() => {
        loadBalancerFilterService.updateLoadBalancerGroups(app);
        $scope.groups = LoadBalancerFilterModel.groups;
        $scope.tags = LoadBalancerFilterModel.tags;
        // Timeout because the updateLoadBalancerGroups method is debounced by 25ms
        $timeout(() => { this.initialized = true; }, 50);
      });
    };

    this.clearFilters = function() {
      loadBalancerFilterService.clearFilters();
      updateLoadBalancerGroups();
    };

    this.createLoadBalancer = function createLoadBalancer() {
      providerSelectionService.selectProvider(app, 'loadBalancer').then(function(selectedProvider) {
        let provider = cloudProviderRegistry.getValue(selectedProvider, 'loadBalancer');
        $uibModal.open({
          templateUrl: provider.createLoadBalancerTemplateUrl,
          controller: `${provider.createLoadBalancerController} as ctrl`,
          size: 'lg',
          resolve: {
            application: function() { return app; },
            loadBalancer: function() { return null; },
            isNew: function() { return true; },
            forPipelineConfig: function() { return false; }
          }
        });
      });
    };

    this.updateLoadBalancerGroups = _.debounce(updateLoadBalancerGroups, 200);

    if (app.loadBalancers.loaded) {
      updateLoadBalancerGroups();
    }

    app.activeState = app.loadBalancers;
    $scope.$on('$destroy', () => app.activeState = app);

    app.loadBalancers.onRefresh($scope, updateLoadBalancerGroups);
  }
);
