import { module } from 'angular';

import { TaskNotFound } from './TaskNotFound';
import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider } from '../application/application.state.provider';
import { INestedState, StateConfigProvider } from '../navigation/state.provider';
import { TaskReader } from './task.read.service';

export const TASK_STATES = 'spinnaker.core.task.states';
module(TASK_STATES, [APPLICATION_STATE_PROVIDER]).config([
  'applicationStateProvider',
  'stateConfigProvider',
  (applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {
    const taskDetails: INestedState = {
      name: 'taskDetails',
      url: '/:taskId',
      views: {},
      data: {
        pageTitleDetails: {
          title: 'Task Details',
          nameParam: 'taskId',
        },
      },
    };

    const tasks: INestedState = {
      name: 'tasks',
      url: '/tasks?q',
      views: {
        insight: {
          templateUrl: require('../task/tasks.html'),
          controller: 'TasksCtrl',
          controllerAs: 'tasks',
        },
      },
      params: {
        q: { dynamic: true, value: null },
      },
      data: {
        pageTitleSection: {
          title: 'Tasks',
        },
      },
      children: [taskDetails],
    };

    const taskLookup: INestedState = {
      name: 'taskLookup',
      url: '/tasks/:taskId',
      params: {
        taskId: { dynamic: true },
      },
      redirectTo: (transition) => {
        const { taskId } = transition.params();

        if (!taskId) {
          return undefined;
        }

        return Promise.resolve(TaskReader.getTask(taskId))
          .then((task) =>
            transition.router.stateService.target('home.applications.application.tasks.taskDetails', {
              application: task.application,
              taskId,
            }),
          )
          .catch(() => {});
      },
      views: {
        'main@': { component: TaskNotFound, $type: 'react' },
      },
    };

    applicationStateProvider.addChildState(tasks);

    stateConfigProvider.addToRootState(taskLookup);
  },
]);
