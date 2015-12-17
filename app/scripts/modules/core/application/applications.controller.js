'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.applications.controller', [
  require('./service/applications.read.service.js'),
  require('../account/account.service.js'),
  require('../presentation/anyFieldFilter/anyField.filter.js'),
  require('../cache/viewStateCache.js'),
  require('../presentation/sortToggle/sorttoggle.directive.js'),
  require('../insight/insightmenu.directive.js'),
  require('../overrideRegistry/override.registry.js'),
])
  .controller('ApplicationsCtrl', function($scope, $uibModal, $log, $filter, accountService,
                                           $state, applicationReader, viewStateCache, overrideRegistry) {

    var applicationsViewStateCache = viewStateCache.applications || viewStateCache.createCache('applications', { version: 1 });

    function cacheViewState() {
      applicationsViewStateCache.put('#global', $scope.viewState);
    }

    function initializeViewState() {
      $scope.viewState = applicationsViewStateCache.get('#global') || {
          sortModel: { key: 'name' },
          applicationFilter: '',
        };
    }

    $scope.applicationsLoaded = false;

    $scope.applicationFilter = '';

    accountService.listAccounts().then(function(accounts) {
      $scope.accounts = accounts;
    });

    function routeToApplication(app) {
      $state.go(
        'home.applications.application.insight.clusters', {
          application: app.name,
        }
      );
    }

    $scope.menuActions = [
      {
        displayName: 'Create Application',
        action: function() {
          $uibModal.open({
            scope: $scope,
            templateUrl: overrideRegistry.getTemplate('createApplicationModal', require('./modal/newapplication.html')),
            controller: overrideRegistry.getController('CreateApplicationModalCtrl'),
            controllerAs: 'newAppModal'
          }).result.then(routeToApplication);
        }
      }
    ];

    this.filterApplications = function filterApplications() {
      var filtered = $filter('anyFieldFilter')($scope.applications, {name: $scope.viewState.applicationFilter, email: $scope.viewState.applicationFilter, accounts: $scope.viewState.applicationFilter}),
        sorted = $filter('orderBy')(filtered, $scope.viewState.sortModel.key);
      $scope.filteredApplications = sorted;
      this.resetPaginator();
    };

    this.resultPage = function resultPage() {
      var pagination = $scope.pagination,
        allFiltered = $scope.filteredApplications,
        start = (pagination.currentPage - 1) * pagination.itemsPerPage,
        end = pagination.currentPage * pagination.itemsPerPage;
      if (!allFiltered || !allFiltered.length) {
        return [];
      }
      if (allFiltered.length < pagination.itemsPerPage) {
        return allFiltered;
      }
      if (allFiltered.length < end) {
        return allFiltered.slice(start);
      }
      return allFiltered.slice(start, end);
    };

    this.resetPaginator = function resetPaginator() {
      $scope.pagination = {
        currentPage: 1,
        itemsPerPage: 12,
        maxSize: 12
      };
    };

    var ctrl = this;

    function fixAccount(application) {
      if (application.accounts) {
        application.accounts = application.accounts.split(',').sort().join(', ');
      }
    }

    applicationReader.listApplications().then(function(applications) {
      applications.forEach(fixAccount);
      $scope.applications = applications;
      ctrl.filterApplications();
      $scope.applicationsLoaded = true;
    });

    $scope.$watch('viewState', cacheViewState, true);

    initializeViewState();

  }
);
