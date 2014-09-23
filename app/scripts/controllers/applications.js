'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ApplicationsCtrl', function($scope, $exceptionHandler, $modal, $log, $filter, RxService, front50, notifications, oortService, searchService) {

    $scope.applicationsLoaded = false;

    $scope.sorting = {
      sortKey: 'name',
      reverse: false
    };

    $scope.applicationFilter = '';

    $scope.menuActions = [
      {
        displayName: 'Create Application',
        action: function() {
          $modal.open({
            templateUrl: 'views/newapplication.html'
            // TODO: how to create a new scope
          }).result.then(function(app) {
            front50.all('applications').post(app).then(function(resp) {
              $log.debug(resp);
              notifications.observableTask({
                title: 'Creating application ' + app.name,
                message: app.name + ' Created!',
                observable: $scope.applications.flatMap(function(applications) {
                  return RxService.Observable.fromArray(applications)
                  .filter(function(other) {
                    return other.name === app.name;
                  });
                })
              });
            }, function(err) {
              $exceptionHandler(err);
            });
          });
        }
      }
    ];

    searchService.search({q:'', type: 'applications', pageSize:100000}).then(function(response) {
      if (!$scope.applications) {
        $scope.applications = response.data[0].results.map(function (app) {
          return {name: app.application};
        });
        ctrl.filterApplications();
        $scope.partiallyLoaded = true;
        $scope.applicationsLoaded = true;
      }
    });

    this.filterApplications = function filterApplications() {
      var filtered = $filter('filter')($scope.applications, {name: $scope.applicationFilter}),
        sorted = $filter('orderBy')(filtered, $scope.sorting.sortKey, $scope.sorting.reverse);
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
      $scope.partiallyLoaded = false;
      $scope.fullyLoaded = true;
    });

  }
);
