'use strict';


angular.module('deckApp')
  .controller('ApplicationsCtrl', function($scope, $exceptionHandler, $modal, $log, $filter, accountService,
                                           notifications, oortService , orcaService, urlBuilder, $state) {

    $scope.applicationsLoaded = false;

    $scope.sortModel = {
      sortKey: 'name',
      reverse: false
    };

    $scope.applicationFilter = '';

    accountService.listAccounts().then(function(accounts) {
      $scope.accounts = accounts;
    });

    function newAppNotification(app) {
      notifications.create({
        title: 'Creating application '+app.name,
      });
    }

    function appCreatedNotification(app) {
      notifications.create({
        title: app.name,
        message: 'Created!',
        href: urlBuilder.buildFromMetadata({
          type: 'applications',
          application: app.name,
        }),
      });
    }

    function routeToApplication(app) {
      $state.go(
        'home.applications.application', {
          application: app.name,
        }
      );
    }

    function successModalClose (app) {
      newAppNotification(app);
      appCreatedNotification(app);
      routeToApplication(app);
    }

    $scope.menuActions = [
      {
        displayName: 'Create Application',
        action: function() {
          $modal.open({
            scope: $scope,
            templateUrl: 'views/newapplication.html',
            controller: 'CreateApplicationModalCtrl',
            controllerAs: 'newAppModal'
          }).result.then(successModalClose);
        }
      }
    ];

    this.filterApplications = function filterApplications() {
      var filtered = $filter('anyFieldFilter')($scope.applications, {name: $scope.applicationFilter, email: $scope.applicationFilter}),
        sorted = $filter('orderBy')(filtered, $scope.sortModel.sortKey, $scope.sortModel.reverse);
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

    oortService.listApplications().then(function(applications) {
      $scope.applications = applications;
      ctrl.filterApplications();
      $scope.applicationsLoaded = true;
    });

  }
);
