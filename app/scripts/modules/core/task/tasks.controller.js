'use strict';

import _ from 'lodash';
import {VIEW_STATE_CACHE_SERVICE} from 'core/cache/viewStateCache.service';
import {DISPLAYABLE_TASKS_FILTER} from './displayableTasks.filter';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.task.controller', [
  require('angular-ui-router'),
  require('./taskProgressBar.directive.js'),
  VIEW_STATE_CACHE_SERVICE,
  require('./task.write.service.js'),
  require('../confirmationModal/confirmationModal.service.js'),
  DISPLAYABLE_TASKS_FILTER,
  require('../config/settings.js'),
])
  .controller('TasksCtrl', function ($scope, $state, $q, settings, app, viewStateCache, taskWriter, confirmationModalService) {

    if (app.notFound) {
      return;
    }

    var controller = this;
    const application = app;

    $scope.$state = $state;

    var tasksViewStateCache = viewStateCache.get('tasks') || viewStateCache.createCache('tasks', { version: 1 });

    function cacheViewState() {
      tasksViewStateCache.put(application.name, $scope.viewState);
      cacheGlobalViewState();
    }

    function cacheGlobalViewState() {
      tasksViewStateCache.put('#common', {
        itemsPerPage: $scope.viewState.itemsPerPage,
      });
    }

    $scope.tasksUrl = [settings.gateUrl, 'applications', application.name, 'tasks/'].join('/');
    $scope.filterCountOptions = [10, 20, 30, 50, 100, 200];

    function initializeViewState() {
      var viewState = tasksViewStateCache.get(application.name) || {
        taskStateFilter: '',
        nameFilter: '',
        expandedTasks: [],
      };
      viewState.loading = true;
      viewState.itemsPerPage = tasksViewStateCache.get('#common') ? tasksViewStateCache.get('#common').itemsPerPage : 20;

      $scope.viewState = viewState;
    }

    controller.taskStateFilter = 'All';
    controller.application = application;

    controller.sortedTasks = [];

    controller.toggleDetails = function(taskId) {
      var index = $scope.viewState.expandedTasks.indexOf(taskId);
      if (index === -1) {
        $scope.viewState.expandedTasks.push(taskId);
      } else {
        $scope.viewState.expandedTasks.splice(index, 1);
      }
    };

    controller.isExpanded = function(taskId) {
      return taskId && $scope.viewState.expandedTasks.includes(taskId);
    };

    controller.sortTasksAndResetPaginator = function() {
      controller.sortTasks();
      controller.resetPaginator();
    };

    controller.sortTasks = function() {
      var joinedLists = filterRunningTasks().concat(filterNonRunningTasks());
      controller.sortedTasks = joinedLists;
      if ($scope.viewState.nameFilter) {
        var normalizedSearch = $scope.viewState.nameFilter.toLowerCase();
        controller.sortedTasks = _.filter(joinedLists, function(task) {
          return task.name.toLowerCase().includes(normalizedSearch) ||
            task.id.toLowerCase().includes(normalizedSearch) ||
            (task.getValueFor('credentials') || '').toLowerCase().includes(normalizedSearch) ||
            (task.getValueFor('region') || '').toLowerCase().includes(normalizedSearch) ||
            (task.getValueFor('regions') || []).join(' ').toLowerCase().includes(normalizedSearch) ||
            (task.getValueFor('user') || '').toLowerCase().includes(normalizedSearch);
        });
      }
      if ($scope.viewState.taskStateFilter) {
        controller.sortedTasks = _.filter(controller.sortedTasks, { status: $scope.viewState.taskStateFilter });
      }
    };

    controller.clearNameFilter = function() {
      $scope.viewState.nameFilter = '';
      controller.nameFilterUpdated();
    };

    controller.nameFilterUpdated = function() {
      if ($state.includes('**.taskDetails')) {
        $state.go('^');
      }
      controller.sortTasksAndResetPaginator();
    };

    controller.cancelTask = function(taskId) {
      var task = application.tasks.data.filter(function(task) { return task.id === taskId; })[0];
      var submitMethod = function () {
        return taskWriter.cancelTask(application.name, taskId).then(() => application.tasks.refresh());
      };

      confirmationModalService.confirm({
        header: 'Really cancel ' + task.name + '?',
        buttonText: 'Yes',
        cancelButtonText: 'No',
        submitMethod: submitMethod
      });
    };

    controller.deleteTask = function(taskId) {
      var task = application.tasks.data.filter(function(task) { return task.id === taskId; })[0];
      var submitMethod = function () {
        return taskWriter.deleteTask(taskId).then(() => application.tasks.refresh());
      };

      confirmationModalService.confirm({
        header: 'Really delete history for ' + task.name + '?',
        body: '<p>This will permanently delete the task history.</p>',
        buttonText: 'Delete',
        submitMethod: submitMethod
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
      var pagination = $scope.pagination,
        allFiltered = controller.sortedTasks,
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


    controller.getFirstDeployServerGroupName = function(task) {
      if(task.execution && task.execution.stages) {
        var stage = findStageWithTaskInExecution(task.execution, ['createCopyLastAsg', 'createDeploy', 'cloneServerGroup', 'createServerGroup']);
        return _.chain(stage)
          .get('context')
          .get('deploy.server.groups')
          .values()
          .head()
          .head()
          .value();
      }
    };

    controller.getAccountId = function(task) {
      return _.chain(task.variables)
        .find({'key': 'account'})
        .result('value')
        .value();
    };

    controller.getRegion = function (task) {
      var deployedServerGroups = _.find(task.variables, function(variable) {
        return variable.key === 'deploy.server.groups';
      }).value;

      return _.keys(deployedServerGroups)[0];
    };

    controller.getProviderForServerGroupByTask = function(task) {
      var serverGroupName = controller.getFirstDeployServerGroupName(task);
      return _.chain(application.serverGroups.data)
        .find(function(serverGroup) {
          return serverGroup.name === serverGroupName;
        })
        .result('type')
        .value();
    };

    function findStageWithTaskInExecution(execution, stageName) {
      return _.chain(execution.stages).find(function(stage) {
        return _.some(stage.tasks, function(task) {
          return stageName.includes(task.name);
        });
      }).value();
    }

    function filterRunningTasks() {
      var running = _.chain(application.tasks.data)
        .filter(function(task) {
          return task.name && task.isActive;
        })
        .value();

      return running.sort(taskStartTimeComparator);
    }

    function filterNonRunningTasks() {
      var notRunning = _.chain(application.tasks.data)
        .filter(function(task) {
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

    // The taskId will not be available in the $stateParams that would be passed into this controller
    // because that field belongs to a child state. So we have to watch for a $stateChangeSuccess event, then set
    // the value on the scope
    $scope.$on('$stateChangeSuccess', function(event, toState, toParams) {
      var taskId = toParams.taskId;
      if (!$scope.viewState.expandedTasks.includes(taskId)) {
        controller.toggleDetails(taskId);
      }
      $scope.viewState.nameFilter = taskId;
      $scope.viewState.taskStateFilter = '';
      controller.sortTasksAndResetPaginator();
    });

    initializeViewState();

    application.tasks.activate();

    application.tasks.ready().then(() => {
      $scope.viewState.loading = false;
      $scope.viewState.loadError = app.tasks.loadFailure;
      if (!app.tasks.loadFailure) {
        this.sortTasks();
      }
    });

    application.activeState = application.tasks;
    $scope.$on('$destroy', () => {
      application.activeState = application;
      application.tasks.deactivate();
    });

    this.application.tasks.onRefresh($scope, this.sortTasks);
  }
);
