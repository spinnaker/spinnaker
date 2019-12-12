'use strict';
import { module } from 'angular';

import { ANY_FIELD_FILTER } from '../presentation/anyFieldFilter/anyField.filter';
import { INSIGHT_MENU_DIRECTIVE } from '../insight/insightmenu.directive';
import { ViewStateCache } from 'core/cache';

import { ConfigureProjectModal } from './configure';
import { ProjectReader } from './service/ProjectReader';
import { CORE_PRESENTATION_SORTTOGGLE_SORTTOGGLE_DIRECTIVE } from '../presentation/sortToggle/sorttoggle.directive';
import UIROUTER_ANGULARJS from '@uirouter/angularjs';

export const CORE_PROJECTS_PROJECTS_CONTROLLER = 'spinnaker.projects.controller';
export const name = CORE_PROJECTS_PROJECTS_CONTROLLER; // for backwards compatibility
module(CORE_PROJECTS_PROJECTS_CONTROLLER, [
  UIROUTER_ANGULARJS,
  ANY_FIELD_FILTER,
  CORE_PRESENTATION_SORTTOGGLE_SORTTOGGLE_DIRECTIVE,
  INSIGHT_MENU_DIRECTIVE,
]).controller('ProjectsCtrl', [
  '$scope',
  '$uibModal',
  '$log',
  '$filter',
  function($scope, $uibModal, $log, $filter) {
    let projectsViewStateCache =
      ViewStateCache.get('projects') || ViewStateCache.createCache('projects', { version: 1 });

    function cacheViewState() {
      projectsViewStateCache.put('#global', $scope.viewState);
    }

    function initializeViewState() {
      $scope.viewState = projectsViewStateCache.get('#global') || {
        sortModel: { key: 'name' },
        projectFilter: '',
      };
    }

    $scope.projectsLoaded = false;

    $scope.projectFilter = '';

    $scope.menuActions = [
      {
        displayName: 'Create Project',
        action: function() {
          ConfigureProjectModal.show().catch(() => {});
        },
      },
    ];

    this.filterProjects = function filterProjects() {
      let filtered = $filter('anyFieldFilter')($scope.projects, {
          name: $scope.viewState.projectFilter,
          email: $scope.viewState.projectFilter,
        }),
        sorted = $filter('orderBy')(filtered, $scope.viewState.sortModel.key);
      $scope.filteredProjects = sorted;
      this.resetPaginator();
    };

    this.resultPage = function resultPage() {
      let pagination = $scope.pagination,
        allFiltered = $scope.filteredProjects,
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
        maxSize: 12,
      };
    };

    let ctrl = this;

    ProjectReader.listProjects().then(function(projects) {
      $scope.projects = projects;
      ctrl.filterProjects();
      $scope.projectsLoaded = true;
    });

    $scope.$watch('viewState', cacheViewState, true);

    initializeViewState();
  },
]);
