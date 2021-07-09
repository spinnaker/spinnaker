import * as angular from 'angular';
import _ from 'lodash';
import { react2angular } from 'react2angular';

import { PROJECT_SUMMARY_POD_COMPONENT } from '../infrastructure/projectSummaryPod.component';
import { RECENTLY_VIEWED_ITEMS_COMPONENT } from '../infrastructure/recentlyViewedItems.component';
import { SEARCH_RESULT_COMPONENT } from '../infrastructure/searchResult.component';
import { INFRASTRUCTURE_SEARCH_SERVICE } from './infrastructureSearch.service';
import { InsightMenu as SearchInsightMenu } from '../../insight/InsightMenu';
import { OVERRIDE_REGISTRY } from '../../overrideRegistry/override.registry';
import { PAGE_TITLE_SERVICE } from '../../pageTitle/pageTitle.service';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';
import { ConfigureProjectModal } from '../../projects';
import { SearchService } from '../search.service';
import { SEARCH_RANK_FILTER } from '../searchRank.filter';
import { FirewallLabels } from '../../securityGroup/label';
import { ClusterState } from '../../state';
import { SPINNER_COMPONENT } from '../../widgets/spinners/spinner.component';

('use strict');

export const CORE_SEARCH_INFRASTRUCTURE_INFRASTRUCTURE_CONTROLLER = 'spinnaker.search.infrastructure.controller';
export const name = CORE_SEARCH_INFRASTRUCTURE_INFRASTRUCTURE_CONTROLLER; // for backwards compatibility
angular
  .module(CORE_SEARCH_INFRASTRUCTURE_INFRASTRUCTURE_CONTROLLER, [
    INFRASTRUCTURE_SEARCH_SERVICE,
    SEARCH_RESULT_COMPONENT,
    PAGE_TITLE_SERVICE,
    PROJECT_SUMMARY_POD_COMPONENT,
    SEARCH_RANK_FILTER,
    OVERRIDE_REGISTRY,
    RECENTLY_VIEWED_ITEMS_COMPONENT,
    SPINNER_COMPONENT,
  ])
  .component(
    'searchInsightMenu',
    react2angular(withErrorBoundary(SearchInsightMenu, 'searchInsightMenu'), [
      'createApp',
      'createProject',
      'refreshCaches',
    ]),
  )
  .controller('InfrastructureCtrl', [
    '$scope',
    'infrastructureSearchService',
    '$stateParams',
    '$location',
    'overrideRegistry',
    'pageTitleService',
    '$uibModal',
    '$state',
    function (
      $scope,
      infrastructureSearchService,
      $stateParams,
      $location,
      overrideRegistry,
      pageTitleService,
      $uibModal,
      $state,
    ) {
      const search = infrastructureSearchService.getSearcher();

      $scope.firewallsLabel = FirewallLabels.get('firewalls');

      $scope.categories = [];
      $scope.projects = [];

      $scope.viewState = {
        searching: false,
        minCharactersToSearch: 3,
      };

      this.clearFilters = (r) => ClusterState.filterService.overrideFiltersForUrl(r);

      function updateLocation() {
        $location.search('q', $scope.query || null);
        $location.replace();
      }

      $scope.pageSize = SearchService.DEFAULT_PAGE_SIZE;
      let autoNavigate = false;

      if (angular.isDefined($location.search().q)) {
        $scope.query = $location.search().q;
        autoNavigate = !!$location.search().route;
        // clear the parameter - it only comes from shortcut links, and if there are more than one result,
        // we don't want to automatically route the user or have them copy this as a link
        $location.search('route', null);
      }
      $scope.$watch('query', function (query) {
        $scope.categories = [];
        $scope.projects = [];
        if (query && query.length < $scope.viewState.minCharactersToSearch) {
          $scope.viewState.searching = false;
          updateLocation();
          return;
        }
        $scope.viewState.searching = true;
        search.query(query).then(function (resultSets) {
          const allResults = _.flatten(resultSets.map((r) => r.results));
          if (allResults.length === 1 && autoNavigate) {
            $location.url(allResults[0].href.substring(1));
          } else {
            // clear auto-navigation so, if the user does another search, and that returns a single result, we don't
            // surprise them by navigating to it
            autoNavigate = false;
          }
          $scope.categories = resultSets
            .filter((resultSet) => resultSet.type.id !== 'projects' && resultSet.results.length)
            .sort((a, b) => a.type.id - b.type.id);
          $scope.projects = resultSets.filter(
            (resultSet) => resultSet.type.id === 'projects' && resultSet.results.length,
          );
          $scope.moreResults =
            _.sumBy(resultSets, function (resultSet) {
              return resultSet.results.length;
            }) === $scope.pageSize;
          updateLocation();
          pageTitleService.handleRoutingSuccess({
            pageTitleMain: {
              label: query ? ' search results for "' + query + '"' : 'Infrastructure',
            },
          });
          $scope.viewState.searching = false;
        });
      });

      this.createProject = () => ConfigureProjectModal.show().catch(() => {});

      this.createApplicationForTests = () => {
        $uibModal
          .open({
            scope: $scope,
            templateUrl: overrideRegistry.getTemplate(
              'createApplicationModal',
              require('../../application/modal/newapplication.html'),
            ),
            controller: overrideRegistry.getController('CreateApplicationModalCtrl'),
            controllerAs: 'newAppModal',
          })
          .result.then(routeToApplication)
          .catch(() => {});
      };

      function routeToApplication(app) {
        $state.go('home.applications.application', {
          application: app.name,
        });
      }

      this.menuActions = [
        {
          displayName: 'Create Project',
          action: this.createProject,
        },
        {
          displayName: 'Create Application',
          action: this.createApplicationForTests,
        },
      ];

      this.hasResults = () => {
        return $scope.categories.length || $scope.projects.length;
      };

      this.noMatches = () => !this.hasResults() && $scope.query && $scope.query.length > 0;

      this.showRecentResults = () =>
        !$scope.viewState.searching &&
        !$scope.projects.length &&
        $scope.categories.every((category) => !category.results.length);
    },
  ])
  .directive('infrastructureSearchV1', function () {
    return {
      restrict: 'E',
      templateUrl: require('./infrastructure.html'),
      controller: 'InfrastructureCtrl',
      controllerAs: 'ctrl',
    };
  });
