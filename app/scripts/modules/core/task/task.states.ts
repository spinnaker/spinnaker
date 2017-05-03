import {module} from 'angular';

import {INestedState} from 'core/navigation/state.provider';
import {
  APPLICATION_STATE_PROVIDER, ApplicationStateProvider,
} from 'core/application/application.state.provider';

export const TASK_STATES = 'spinnaker.core.task.states';
module(TASK_STATES, [
  APPLICATION_STATE_PROVIDER
]).config((applicationStateProvider: ApplicationStateProvider) => {

  const taskDetails: INestedState = {
    name: 'taskDetails',
    url: '/:taskId',
    views: {},
    data: {
      pageTitleDetails: {
        title: 'Task Details',
        nameParam: 'taskId'
      }
    }
  };

  const tasks: INestedState = {
    name: 'tasks',
    url: '/tasks',
    views: {
      'insight': {
        templateUrl: require('../task/tasks.html'),
        controller: 'TasksCtrl',
        controllerAs: 'tasks'
      },
    },
    data: {
      pageTitleSection: {
        title: 'Tasks'
      }
    },
    children: [taskDetails],
  };

  applicationStateProvider.addChildState(tasks);
});
