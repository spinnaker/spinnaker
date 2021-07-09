'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';
import _ from 'lodash';

import { ViewStateCache } from '../cache';
import { SETTINGS } from '../config/settings';
import { ConfirmationModalService } from '../confirmationModal';

import { DISPLAYABLE_TASKS_FILTER } from './displayableTasks.filter';
import { TaskWriter } from './task.write.service';
import { CORE_TASK_TASKPROGRESSBAR_DIRECTIVE } from './taskProgressBar.directive';

export const CORE_TASK_TASKS_CONTROLLER = 'spinnaker.core.task.controller';
export const name = CORE_TASK_TASKS_CONTROLLER; // for backwards compatibility
module(CORE_TASK_TASKS_CONTROLLER, [
  UIROUTER_ANGULARJS,
  CORE_TASK_TASKPROGRESSBAR_DIRECTIVE,
  DISPLAYABLE_TASKS_FILTER,
]).controller('TasksCtrl', [
  '$scope',
  '$state',
  '$stateParams',
  '$q',
  'app',
  function ($scope, $state, $stateParams, $q, app) {
    if (app.notFound || app.hasError) {
      return;
    }

    const controller = this;
    const application = app;

    $scope.$state = $state;

    const tasksViewStateCache = ViewStateCache.get('tasks') || ViewStateCache.createCache('tasks', { version: 1 });

    function cacheViewState() {
      tasksViewStateCache.put(application.name, $scope.viewState);
      cacheGlobalViewState();
    }

    function cacheGlobalViewState() {
      tasksViewStateCache.put('#common', {
        itemsPerPage: $scope.viewState.itemsPerPage,
      });
    }

    $scope.tasksUrl = [SETTINGS.gateUrl, 'applications', application.name, 'tasks/'].join('/');
    $scope.filterCountOptions = [10, 20, 30, 50, 100, 200];

    function initializeViewState() {
      const viewState = tasksViewStateCache.get(application.name) || {
        taskStateFilter: '',
        expandedTasks: [],
      };
      viewState.nameFilter = $stateParams.q || '';
      viewState.loading = true;
      viewState.cancelling = false;
      viewState.itemsPerPage = tasksViewStateCache.get('#common')
        ? tasksViewStateCache.get('#common').itemsPerPage
        : 20;

      $scope.viewState = viewState;
      if ($stateParams.taskId) {
        setTaskFilter();
      }
    }

    const setTaskFilter = () => {
      const taskId = $stateParams.taskId;
      if (!$scope.viewState.expandedTasks.includes(taskId)) {
        controller.toggleDetails(taskId);
      }
      $scope.viewState.nameFilter = taskId;
      $scope.viewState.taskStateFilter = '';
      controller.sortTasksAndResetPaginator();
    };

    controller.taskStateFilter = 'All';
    controller.application = application;

    controller.sortedTasks = [];

    controller.toggleDetails = function (taskId) {
      const index = $scope.viewState.expandedTasks.indexOf(taskId);
      if (index === -1) {
        $scope.viewState.expandedTasks.push(taskId);
      } else {
        $scope.viewState.expandedTasks.splice(index, 1);
      }
    };

    controller.isExpanded = function (taskId) {
      return taskId && $scope.viewState.expandedTasks.includes(taskId);
    };

    controller.sortTasksAndResetPaginator = function () {
      controller.sortTasks();
      controller.resetPaginator();
    };

    controller.sortTasks = function () {
      const joinedLists = filterRunningTasks().concat(filterNonRunningTasks());
      controller.sortedTasks = joinedLists;
      if ($scope.viewState.nameFilter) {
        const normalizedSearch = $scope.viewState.nameFilter.toLowerCase();
        controller.sortedTasks = _.filter(joinedLists, function (task) {
          return (
            task.name.toLowerCase().includes(normalizedSearch) ||
            task.id.toLowerCase().includes(normalizedSearch) ||
            (task.getValueFor('credentials') || '').toLowerCase().includes(normalizedSearch) ||
            (task.getValueFor('region') || '').toLowerCase().includes(normalizedSearch) ||
            (task.getValueFor('regions') || []).join(' ').toLowerCase().includes(normalizedSearch) ||
            (task.getValueFor('user') || '').toLowerCase().includes(normalizedSearch) ||
            _.get(task, 'execution.authentication.user', '').toLowerCase().includes(normalizedSearch)
          );
        });
      }
      if ($scope.viewState.taskStateFilter) {
        controller.sortedTasks = _.filter(controller.sortedTasks, { status: $scope.viewState.taskStateFilter });
      }
    };

    controller.clearNameFilter = function () {
      $scope.viewState.nameFilter = '';
      controller.nameFilterUpdated();
    };

    controller.nameFilterUpdated = _.debounce(() => {
      const params = { q: $scope.viewState.nameFilter };
      if ($state.includes('**.taskDetails')) {
        $state.go('^', params);
      } else {
        $state.go('.', params);
      }
      controller.sortTasksAndResetPaginator();
    }, 300);

    controller.cancelTask = function (taskId) {
      const task = application.tasks.data.filter(function (task) {
        return task.id === taskId;
      })[0];
      const submitMethod = function () {
        // cancelTask() polls aggressively waiting for a sucessful cancellation,
        // which triggers equally aggressive updates to the runningTimeInMs field
        // on the hydrated task object. Because we render that field in templates,
        // updating so quickly can make Angular think we're doing Bad Things(tm)
        // and abort its change detection. Instead, we stop rendering that field
        // while the polling is active.
        $scope.viewState.cancelling = true;
        return TaskWriter.cancelTask(taskId).then(() => {
          $scope.viewState.cancelling = false;
          application.tasks.refresh();
        });
      };

      ConfirmationModalService.confirm({
        header: 'Really cancel ' + task.name + '?',
        buttonText: 'Yes',
        cancelButtonText: 'No',
        submitMethod: submitMethod,
      });
    };

    /**
     * Pagination - largely copied from applications.controller.js
     */

    controller.resetPaginator = function resetPaginator() {
      $scope.pagination = {
        currentPage: 1,
        itemsPerPage: $scope.viewState.itemsPerPage,
        maxSize: 12,
      };
    };

    controller.resultPage = function resultPage() {
      const pagination = $scope.pagination;
      const allFiltered = controller.sortedTasks;
      const start = (pagination.currentPage - 1) * pagination.itemsPerPage;
      const end = pagination.currentPage * pagination.itemsPerPage;
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

    controller.getFirstDeployServerGroupName = function (task) {
      if (task.execution && task.execution.stages) {
        const stage = findStageWithTaskInExecution(task.execution, [
          'createCopyLastAsg',
          'createDeploy',
          'cloneServerGroup',
          'createServerGroup',
        ]);
        return _.chain(stage).get('context').get('deploy.server.groups').values().head().head().value();
      }
    };

    controller.getAccountId = function (task) {
      return _.chain(task.variables).find({ key: 'account' }).result('value').value();
    };

    controller.getRegion = function (task) {
      const regionVariable = (task.variables || []).find((variable) => {
        return (
          ['deploy.server.groups', 'availabilityZones'].includes(variable.key) && Object.keys(variable.value).length
        );
      });
      return regionVariable && Object.keys(regionVariable.value)[0];
    };

    controller.getProviderForServerGroupByTask = function (task) {
      const serverGroupName = controller.getFirstDeployServerGroupName(task);
      return _.chain(application.serverGroups.data)
        .find(function (serverGroup) {
          return serverGroup.name === serverGroupName;
        })
        .result('type')
        .value();
    };

    function findStageWithTaskInExecution(execution, stageName) {
      return _.chain(execution.stages)
        .find(function (stage) {
          return _.some(stage.tasks, function (task) {
            return stageName.includes(task.name);
          });
        })
        .value();
    }

    function filterRunningTasks() {
      const running = _.chain(application.tasks.data)
        .filter(function (task) {
          return task.name && task.isActive;
        })
        .value();

      return running.sort(taskStartTimeComparator);
    }

    function filterNonRunningTasks() {
      const notRunning = _.chain(application.tasks.data)
        .filter(function (task) {
          return task.name && !task.isActive;
        })
        .value();

      return notRunning.sort(taskStartTimeComparator);
    }

    function taskStartTimeComparator(taskA, taskB) {
      return taskB.startTime > taskA.startTime ? 1 : taskB.startTime < taskA.startTime ? -1 : 0;
    }

    // angular ui btn-radio doesn't support the ng-change or ng-click directives
    $scope.$watch('viewState.taskStateFilter', controller.sortTasksAndResetPaginator);
    $scope.$watch('viewState', cacheViewState, true);

    initializeViewState();

    application.tasks.activate();

    application.tasks.ready().then(() => {
      $scope.viewState.loading = false;
      $scope.viewState.loadError = app.tasks.loadFailure;
      if (!app.tasks.loadFailure) {
        this.sortTasks();
      }
    });

    application.setActiveState(application.tasks);
    $scope.$on('$destroy', () => {
      application.setActiveState();
      application.tasks.deactivate();
    });

    this.stateChanged = (q) => {
      if ($scope.viewState.nameFilter !== q) {
        $scope.viewState.nameFilter = q;
        this.sortTasksAndResetPaginator();
      } else if ($stateParams.taskId) {
        setTaskFilter();
      }
    };

    $scope.$on('$stateChangeSuccess', (_event, _toState, toParams) => this.stateChanged(toParams.q));

    this.application.tasks.onRefresh($scope, this.sortTasks);
  },
]);
