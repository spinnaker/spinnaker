import {module} from 'angular';
import {StateParams} from 'angular-ui-router';
import {APPLICATION_STATE_PROVIDER, ApplicationStateProvider} from 'core/application/application.state.provider';
import {INestedState, STATE_CONFIG_PROVIDER, StateConfigProvider} from 'core/navigation/state.provider';
import {IProject} from '../domain/IProject';

export interface IProjectStateParms extends StateParams {
  project: string;
}

export const PROJECTS_STATES_CONFIG = 'spinnaker.core.projects.state.config';
module(PROJECTS_STATES_CONFIG, [
  require('./project.controller'),
  require('./projects.controller'),
  require('./dashboard/dashboard.controller'),
  APPLICATION_STATE_PROVIDER,
  STATE_CONFIG_PROVIDER,
]).config((stateConfigProvider: StateConfigProvider, applicationStateProvider: ApplicationStateProvider) => {

  const dashboard: INestedState = {
    name: 'dashboard',
    url: '/dashboard',
    views: {
      detail: {
        templateUrl: require('../projects/dashboard/dashboard.html'),
        controller: 'ProjectDashboardCtrl',
        controllerAs: 'vm',
      }
    },
    data: {
      pageTitleSection: {
        title: 'Dashboard'
      }
    },
  };

  const project: INestedState = {
    name: 'project',
    url: '/projects/{project}',
    resolve: {
      projectConfiguration: ['$stateParams', 'projectReader', ($stateParams: IProjectStateParms, projectReader: any) => {
        return projectReader.getProjectConfig($stateParams.project).then(
          (projectConfig: IProject) => projectConfig,
          (): IProject => {
            return {
              id: null,
              name: $stateParams.project,
              email: null,
              config: null,
              notFound: true,
            };
          }
        );
      }]
    },
    views: {
      'main@': {
        templateUrl: require('../projects/project.html'),
        controller: 'ProjectCtrl',
        controllerAs: 'vm',
      },
    },
    data: {
      pageTitleMain: {
        field: 'project'
      },
      history: {
        type: 'projects'
      }
    },
    children: [
      dashboard
    ]
  };

  const allProjects: INestedState = {
    name: 'projects',
    url: '/projects',
    views: {
      'main@': {
        templateUrl: require('../projects/projects.html'),
        controller: 'ProjectsCtrl',
        controllerAs: 'ctrl'
      }
    },
    data: {
      pageTitleMain: {
        label: 'Projects'
      }
    },
    children: [project],
  };

  stateConfigProvider.addToRootState(allProjects);
  stateConfigProvider.addToRootState(project);
  applicationStateProvider.addParentState(project, 'detail', '/applications');

  stateConfigProvider.addRewriteRule('/projects/{project}', '/projects/{project}/dashboard');
  stateConfigProvider.addRewriteRule('/projects/{project}/applications/{application}', '/projects/{project}/applications/{application}/clusters');

});
