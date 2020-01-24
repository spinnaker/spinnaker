import { module } from 'angular';
import { StateParams } from '@uirouter/angularjs';
import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider } from 'core/application/application.state.provider';
import { INestedState, STATE_CONFIG_PROVIDER, StateConfigProvider } from 'core/navigation/state.provider';
import { ProjectHeader } from './ProjectHeader';
import { IProject } from '../domain/IProject';
import { ProjectReader } from './service/ProjectReader';
import { CORE_PROJECTS_PROJECTS_CONTROLLER } from './projects.controller';
import { CORE_PROJECTS_DASHBOARD_DASHBOARD_CONTROLLER } from './dashboard/dashboard.controller';

export interface IProjectStateParms extends StateParams {
  project: string;
}

export const PROJECTS_STATES_CONFIG = 'spinnaker.core.projects.state.config';
module(PROJECTS_STATES_CONFIG, [
  CORE_PROJECTS_PROJECTS_CONTROLLER,
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
                };
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
        },
      },
      children: [dashboard],
    };

    const allProjects: INestedState = {
      name: 'projects',
      url: '/projects',
      views: {
        'main@': {
          templateUrl: require('../projects/projects.html'),
          controller: 'ProjectsCtrl',
          controllerAs: 'ctrl',
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
    stateConfigProvider.addRewriteRule(
      '/projects/{project}/applications/{application}',
      '/projects/{project}/applications/{application}/clusters',
    );
  },
]);
