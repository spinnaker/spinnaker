'use strict';

const angular = require('angular');

import { CLOUD_PROVIDER_REGISTRY } from 'core/cloudProvider/cloudProvider.registry';
import { LOAD_BALANCER_FILTER_MODEL } from './filter/loadBalancerFilter.model';
import { LOAD_BALANCER_FILTER_SERVICE } from './filter/loadBalancer.filter.service';
import { FILTER_TAGS_COMPONENT } from '../filterModel/filterTags.component';

module.exports = angular.module('spinnaker.core.loadBalancer.controller', [
  require('angular-ui-bootstrap'),
  require('../cloudProvider/providerSelection/providerSelection.service.js'),
  FILTER_TAGS_COMPONENT,
  LOAD_BALANCER_FILTER_SERVICE,
  LOAD_BALANCER_FILTER_MODEL,
  CLOUD_PROVIDER_REGISTRY,
])
  .controller('AllLoadBalancersCtrl', function($scope, $uibModal, $timeout,
                                               providerSelectionService, cloudProviderRegistry,
                                               LoadBalancerFilterModel, loadBalancerFilterService, app ) {

    this.$onInit = () => {
      const groupsUpdatedSubscription = loadBalancerFilterService.groupsUpdatedStream.subscribe(() => groupsUpdated());

      LoadBalancerFilterModel.activate();

      this.initialized = false;

      $scope.application = app;

      $scope.sortFilter = LoadBalancerFilterModel.sortFilter;

      app.loadBalancers.ready().then(() => updateLoadBalancerGroups());

      app.activeState = app.loadBalancers;
      $scope.$on('$destroy', () => {
        app.activeState = app;
        groupsUpdatedSubscription.unsubscribe();
      });

      app.loadBalancers.onRefresh($scope, updateLoadBalancerGroups);
    };

    let groupsUpdated = () => {
      $scope.$applyAsync(() => {
        $scope.groups = LoadBalancerFilterModel.groups;
        $scope.tags = LoadBalancerFilterModel.tags;
      });
    };

    this.groupingsTemplate = require('./groupings.html');

    let updateLoadBalancerGroups = () => {
      $scope.$evalAsync(() => {
        loadBalancerFilterService.updateLoadBalancerGroups(app);
        groupsUpdated();
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

  }
);
