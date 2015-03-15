'use strict';


angular.module('deckApp.applications.controller', [
  'ui.bootstrap',
  'deckApp.applications.read.service',
  'deckApp.account.service'
])
  .controller('ApplicationsCtrl', function($scope, $exceptionHandler, $modal, $log, $filter, accountService,
                                           $state, applicationReader) {

    $scope.applicationsLoaded = false;

    $scope.sortModel = { key: 'name' };

    $scope.applicationFilter = '';

    accountService.listAccounts().then(function(accounts) {
      $scope.accounts = accounts;
    });

    function routeToApplication(app) {
      $state.go(
        'home.applications.application', {
          application: app.name,
        }
      );
    }

    $scope.menuActions = [
      {
        displayName: 'Create Application',
        action: function() {
          $modal.open({
            scope: $scope,
            templateUrl: 'scripts/modules/applications/modal/newapplication.html',
            controller: 'CreateApplicationModalCtrl',
            controllerAs: 'newAppModal'
          }).result.then(routeToApplication);
        }
      }
    ];

    this.filterApplications = function filterApplications() {
      var filtered = $filter('anyFieldFilter')($scope.applications, {name: $scope.applicationFilter, email: $scope.applicationFilter, accounts: $scope.applicationFilter}),
        sorted = $filter('orderBy')(filtered, $scope.sortModel.key);
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

    // Get from cache first
    applicationReader.listApplications().then(function(applications) {
      applications.forEach(fixAccount);
      $scope.applications = applications;
      ctrl.filterApplications();
      $scope.applicationsLoaded = true;

      // Then get from server
      applicationReader.listApplications(true).then(function(applications) {
        applications.forEach(fixAccount);
        $scope.applications = applications;
        ctrl.filterApplications();
      });

    });

  }
);
