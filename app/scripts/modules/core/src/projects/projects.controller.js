'use strict';
const angular = require('angular');

import { ANY_FIELD_FILTER } from '../presentation/anyFieldFilter/anyField.filter';
import { INSIGHT_MENU_DIRECTIVE } from '../insight/insightmenu.directive';
import { ViewStateCache } from 'core/cache';

import { ConfigureProjectModal } from './configure';
import { ProjectReader } from './service/ProjectReader';

module.exports = angular
  .module('spinnaker.projects.controller', [
    require('@uirouter/angularjs').default,
    ANY_FIELD_FILTER,
    require('../presentation/sortToggle/sorttoggle.directive').name,
    INSIGHT_MENU_DIRECTIVE,
  ])
  .controller('ProjectsCtrl', function($scope, $uibModal, $log, $filter, $state) {
    var projectsViewStateCache =
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

    function routeToProject(project) {
      $state.go('home.project.dashboard', {
        project: project.name,
      });
    }

    this.filterProjects = function filterProjects() {
      var filtered = $filter('anyFieldFilter')($scope.projects, {
          name: $scope.viewState.projectFilter,
          email: $scope.viewState.projectFilter,
        }),
        sorted = $filter('orderBy')(filtered, $scope.viewState.sortModel.key);
      $scope.filteredProjects = sorted;
      this.resetPaginator();
    };

    this.resultPage = function resultPage() {
      var pagination = $scope.pagination,
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

    var ctrl = this;

    ProjectReader.listProjects().then(function(projects) {
      $scope.projects = projects;
      ctrl.filterProjects();
      $scope.projectsLoaded = true;
    });

    $scope.$watch('viewState', cacheViewState, true);

    initializeViewState();
  });
