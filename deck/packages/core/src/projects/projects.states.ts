import type { StateParams } from '@uirouter/angularjs';
import { module } from 'angular';

import { ProjectHeader } from './ProjectHeader';
import { Projects } from './Projects';
import type { ApplicationStateProvider } from '../application/application.state.provider';
import { APPLICATION_STATE_PROVIDER } from '../application/application.state.provider';
import { CORE_PROJECTS_DASHBOARD_DASHBOARD_CONTROLLER } from './dashboard/dashboard.controller';
import type { IProject } from '../domain/IProject';
import type { INestedState, StateConfigProvider } from '../navigation/state.provider';
import { STATE_CONFIG_PROVIDER } from '../navigation/state.provider';
import { ProjectReader } from './service/ProjectReader';

export interface IProjectStateParms extends StateParams {
  project: string;
}

export const PROJECTS_STATES_CONFIG = 'spinnaker.core.projects.state.config';
module(PROJECTS_STATES_CONFIG, [
  CORE_PROJECTS_DASHBOARD_DASHBOARD_CONTROLLER,
  APPLICATION_STATE_PROVIDER,
  STATE_CONFIG_PROVIDER,
]).config([
  'stateConfigProvider',
  'applicationStateProvider',
  (stateConfigProvider: StateConfigProvider, applicationStateProvider: ApplicationStateProvider) => {
    const dashboard: INestedState = {
      name: 'dashboard',
      url: '/dashboard',
      views: {
        detail: {
          templateUrl: require('../projects/dashboard/dashboard.html'),
          controller: 'ProjectDashboardCtrl',
          controllerAs: 'vm',
        },
      },
      data: {
        pageTitleSection: {
          title: 'Dashboard',
        },
      },
    };

    const project: INestedState = {
      name: 'project',
      url: '/projects/{project}',
      resolve: {
        projectConfiguration: [
          '$stateParams',
          ($stateParams: IProjectStateParms) => {
            return ProjectReader.getProjectConfig($stateParams.project).then(
              (projectConfig: IProject) => projectConfig,
              (): IProject => {
                return {
                  id: null,
                  name: $stateParams.project,
                  email: null,
                  config: null,
                  notFound: true,
                } as IProject;
              },
            );
          },
        ],
      },
      views: {
        'main@': {
          component: ProjectHeader,
          $type: 'react',
        },
      },
      data: {
        pageTitleMain: {
          field: 'project',
        },
        history: {
          type: 'projects',
          state: 'home.project',
          keyParams: ['project'],
        },
      },
      children: [dashboard],
    };

    const allProjects: INestedState = {
      name: 'projects',
      url: '/projects',
      views: {
        'main@': {
          component: Projects,
          $type: 'react',
        },
      },
      data: {
        pageTitleMain: {
          label: 'Projects',
        },
      },
    };

    stateConfigProvider.addToRootState(allProjects);
    stateConfigProvider.addToRootState(project);
    applicationStateProvider.addParentState(project, 'detail', '/applications');

    stateConfigProvider.addRewriteRule('/projects/{project}', '/projects/{project}/dashboard');
  },
]);
