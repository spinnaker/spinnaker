'use strict';

let angular = require('angular');

require('../../modules/serverGroups/configure/aws/wizard/serverGroupWizard.html');
require('../../modules/serverGroups/configure/gce/wizard/serverGroupWizard.html');
require('./groupings.html');

module.exports = angular.module('clusters.all', [
  require('../clusterFilter/clusterFilterService.js'),
  require('../clusterFilter/clusterFilterModel.js'),
  require('./clusterPod.directive.js'),
  require('../account/account.module.js'),
  require('../providerSelection/providerSelection.module.js'),
  require('../providerSelection/providerSelection.service.js'),
  require('../serverGroups/configure/common/serverGroupCommandBuilder.js'),
  require('../filterModel/filter.tags.directive.js'),
  require('utils/waypoints/waypointContainer.directive.js'),
  require('exports?"ui.bootstrap"!angular-bootstrap'),
])
  .controller('AllClustersCtrl', function($scope, app, $modal, providerSelectionService, _, clusterFilterService,
                                          ClusterFilterModel, serverGroupCommandBuilder) {

    ClusterFilterModel.activate();

    $scope.sortFilter = ClusterFilterModel.sortFilter;

    function addSearchFields() {
      app.serverGroups.forEach(function(serverGroup) {
        var buildInfo = '';
        if (serverGroup.buildInfo && serverGroup.buildInfo.jenkins) {
          buildInfo = [
              '#' + serverGroup.buildInfo.jenkins.number,
              serverGroup.buildInfo.jenkins.host,
              serverGroup.buildInfo.jenkins.name].join(' ').toLowerCase();
        }
        if (!serverGroup.searchField) {
          serverGroup.searchField = [
            serverGroup.region.toLowerCase(),
            serverGroup.name.toLowerCase(),
            serverGroup.account.toLowerCase(),
            buildInfo,
            _.pluck(serverGroup.loadBalancers, 'name').join(' '),
            _.pluck(serverGroup.instances, 'id').join(' ')
          ].join(' ');
        }
      });
    }

    function updateClusterGroups() {
      ClusterFilterModel.applyParamsToUrl();
      $scope.$evalAsync(function() {
          clusterFilterService.updateClusterGroups(app);
        }
      );

      $scope.groups = ClusterFilterModel.groups;
      $scope.tags = ClusterFilterModel.tags;
    }

    this.clearFilters = function() {
      clusterFilterService.clearFilters();
      updateClusterGroups();
    };

    this.createServerGroup = function createServerGroup() {
      // BEN_TODO: figure out interpolated values with webpack
      providerSelectionService.selectProvider().then(function(selectedProvider) {
        $modal.open({
          templateUrl: 'app/scripts/modules/serverGroups/configure/' + selectedProvider + '/wizard/serverGroupWizard.html',
          controller: selectedProvider + 'CloneServerGroupCtrl as ctrl',
          resolve: {
            title: function() { return 'Create New Server Group'; },
            application: function() { return app; },
            serverGroup: function() { return null; },
            serverGroupCommand: function() { return serverGroupCommandBuilder.buildNewServerGroupCommand(app, selectedProvider); },
            provider: function() { return selectedProvider; }
          }
        });
      });
    };

    this.updateClusterGroups = _.debounce(updateClusterGroups, 200);

    function autoRefreshHandler() {
      addSearchFields();
      updateClusterGroups();
    }

    autoRefreshHandler();

    app.registerAutoRefreshHandler(autoRefreshHandler, $scope);
  })
  .name;
