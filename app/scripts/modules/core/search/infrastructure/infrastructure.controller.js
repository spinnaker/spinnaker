'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.search.infrastructure.controller', [
  require('./infrastructureSearch.service.js'),
  require('../../history/recentHistory.service.js'),
  require('../searchResult/searchResult.directive.js'),
  require('../../pageTitle/pageTitle.service.js'),
  require('./project/infrastructureProject.directive.js'),
  require('../searchRank.filter.js'),
  require('../../cluster/filter/clusterFilter.service.js'),
])
  .controller('InfrastructureCtrl', function($scope, infrastructureSearchService, $stateParams, $location, searchService,
                                             pageTitleService, _, recentHistoryService, $uibModal, $state, clusterFilterService) {

    var search = infrastructureSearchService();

    $scope.viewState = {
      searching: false,
      minCharactersToSearch: 3,
    };

    this.clearFilters = clusterFilterService.overrideFiltersForUrl;

    this.loadRecentItems = () => {
      $scope.recentProjects = recentHistoryService.getItems('projects');

      $scope.recentItems = ['applications', 'loadBalancers', 'serverGroups', 'instances', 'securityGroups']
        .map((category) => {
          return {
            category: category,
            results: recentHistoryService.getItems(category)
              .map((result) => {
                let routeParams = angular.extend(result.params, result.extraData);
                search.formatRouteResult(category, routeParams, true).then((name) => result.displayName = name);
                return result;
              })
          };
        })
        .filter((category) => {
          return category.results.length;
        });

      this.hasRecentItems = $scope.recentItems.some((category) => {
        return category.results.length > 0;
      });
    };

    function updateLocation() {
      $location.search('q', $scope.query || null);
      $location.replace();
    }

    $scope.pageSize = searchService.defaultPageSize;

    if (angular.isDefined($location.search().q)) {
      $scope.query = $location.search().q;
    }
    $scope.$watch('query', function(query) {
      if (query && query.length < $scope.viewState.minCharactersToSearch) {
        $scope.categories = [];
        $scope.viewState.searching = false;
        updateLocation();
        return;
      }
      $scope.viewState.searching = true;
      $scope.categories = [];
      search.query(query).then(function(result) {
        $scope.categories = result.filter((category) => category.category !== 'Projects' && category.results.length);
        $scope.projects = result.filter((category) => category.category === 'Projects');
        $scope.moreResults = _.sum(result, function(resultSet) {
          return resultSet.results.length;
        }) === $scope.pageSize;
        updateLocation();
        pageTitleService.handleRoutingSuccess(
          {
            pageTitleMain: {
              label: query ? ' search results for "' + query + '"' : 'Infrastructure'
            }
          }
        );
        $scope.viewState.searching = false;
      });
    });

    this.createProject = () => {
      $uibModal.open({
        scope: $scope,
        templateUrl: require('../../projects/configure/configureProject.modal.html'),
        controller: 'ConfigureProjectModalCtrl',
        controllerAs: 'ctrl',
        resolve: {
          projectConfig: () => { return {}; },
        }
      }).result.then(routeToProject);
    };

    function routeToProject(project) {
      $state.go(
        'home.project.dashboard', {
          project: project.name,
        }
      );
    }

    this.createApplication = () => {
      $uibModal.open({
        scope: $scope,
        templateUrl: require('../../application/modal/newapplication.html'),
        controller: 'CreateApplicationModalCtrl',
        controllerAs: 'newAppModal'
      }).result.then(routeToApplication);
    };

    function routeToApplication(app) {
      $state.go(
        'home.applications.application.insight.clusters', {
          application: app.name,
        }
      );
    }

    this.menuActions = [
      {
        displayName: 'Create Application',
        action: this.createApplication
      },
      {
        displayName: 'Create Project',
        action: this.createProject
      }
    ];

    this.hasResults = () => angular.isObject($scope.categories) && $scope.categories.length > 0 && $scope.query && $scope.query.length > 0;

    this.noMatches = () => angular.isObject($scope.categories) && $scope.categories.length === 0 && $scope.query && $scope.query.length > 0;

    this.showRecentResults = () => this.hasRecentItems && !$scope.viewState.searching && $scope.categories.every((category) => !category.results.length);

    this.removeRecentItem = (category, id) => {
      recentHistoryService.removeItem(category, id);
      this.loadRecentItems();
    };

    this.removeRecentProject = (id, index) => {
      recentHistoryService.removeItem('projects', id);
      this.loadRecentItems();
    };

    this.loadRecentItems();

  }).name;
