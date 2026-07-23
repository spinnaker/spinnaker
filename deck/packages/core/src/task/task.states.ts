import { module } from 'angular';

import { TaskNotFound } from './TaskNotFound';
import { Tasks } from './Tasks';
import type { ApplicationStateProvider } from '../application/application.state.provider';
import { APPLICATION_STATE_PROVIDER } from '../application/application.state.provider';
import type { INestedState, StateConfigProvider } from '../navigation/state.provider';
import { TaskReader } from './task.read.service';

export const TASK_STATES = 'spinnaker.core.task.states';

export function getTasksState(): INestedState {
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

  return {
    name: 'tasks',
    url: '/tasks?q',
    views: {
      insight: {
        component: Tasks,
        $type: 'react',
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
}

module(TASK_STATES, [APPLICATION_STATE_PROVIDER]).config([
  'applicationStateProvider',
  'stateConfigProvider',
  (applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {
    const tasks = getTasksState();

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
