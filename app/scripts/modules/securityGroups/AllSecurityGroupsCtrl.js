'use strict';

let angular = require('angular');

require('./groupings.html');

require('./configure/aws/createSecurityGroup.html');

module.exports = angular.module('spinnaker.securityGroup.all.controller', [
  require('./filter/securityGroup.filter.service.js'),
  require('./filter/securityGroup.filter.model.js'),
  require('utils/lodash.js'),
  require('../providerSelection/providerSelection.service.js'),
  require('../../settings/settings.js'),
  require('exports?"ui.bootstrap"!angular-bootstrap')
])
  .controller('AllSecurityGroupsCtrl', function($scope, app, $modal, _, providerSelectionService, settings,
                                                SecurityGroupFilterModel, securityGroupFilterService) {

    $scope.application = app;

    $scope.sortFilter = SecurityGroupFilterModel.sortFilter;

    function addSearchFields() {
      app.securityGroups.forEach(function(securityGroup) {
        if (!securityGroup.searchField) {
          securityGroup.searchField = [
            securityGroup.name,
            securityGroup.id,
            securityGroup.accountName,
            securityGroup.region,
            _.pluck(securityGroup.usages.serverGroups, 'name').join(' '),
            _.pluck(securityGroup.usages.loadBalancers, 'name').join(' ')
          ].join(' ');
        }
      });
    }

    this.clearFilters = function() {
      securityGroupFilterService.clearFilters();
      updateSecurityGroups();
    };

    function updateSecurityGroups() {
      SecurityGroupFilterModel.applyParamsToUrl();
      $scope.$evalAsync(function () {
        securityGroupFilterService.updateSecurityGroups(app);
        $scope.groups = SecurityGroupFilterModel.groups;
        $scope.tags = SecurityGroupFilterModel.tags;
      });
    }

    this.createSecurityGroup = function createSecurityGroup() {
      providerSelectionService.selectProvider().then(function(provider) {
        var defaultCredentials = app.defaultCredentials || settings.providers.aws.defaults.account,
            defaultRegion = app.defaultRegion || settings.providers.aws.defaults.region;
        $modal.open({
          templateUrl: 'app/scripts/modules/securityGroups/configure/' + provider + '/createSecurityGroup.html',
          controller: 'CreateSecurityGroupCtrl as ctrl',
          resolve: {
            securityGroup: function () {
              return {
                credentials: defaultCredentials,
                subnet: 'none',
                regions: [defaultRegion],
                vpcId: null,
                securityGroupIngress: []
              };
            },
            application: function () {
              return app;
            }
          }
        });
      });
    };

    this.updateSecurityGroups = _.debounce(updateSecurityGroups, 200);

    function autoRefreshHandler() {
      addSearchFields();
      updateSecurityGroups();
    }

    autoRefreshHandler();

    app.registerAutoRefreshHandler(autoRefreshHandler, $scope);
  }
).name;
