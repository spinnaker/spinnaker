import * as React from 'react';
import { IScope } from 'angular';
import { BindAll } from 'lodash-decorators';
import { DropdownButton, MenuItem } from 'react-bootstrap';
import { StateService } from '@uirouter/core';
import { IModalService } from 'angular-ui-bootstrap';

import { Application } from 'core/application';
import { IProject } from 'core/domain';
import { ReactInjector } from 'core/reactShims';
import { OverrideRegistry } from 'core/overrideRegistry';
import { CacheInitializerService } from 'core/cache';

export interface IInsightMenuProps {
  createApp?: boolean;
  createProject?: boolean;
  refreshCaches?: boolean;
}

export interface IInsightMenuState {
  refreshingCache: boolean;
}

@BindAll()
export class InsightMenu extends React.Component<IInsightMenuProps, IInsightMenuState> {
  public static defaultProps: IInsightMenuProps = { createApp: true, createProject: true, refreshCaches: true };

  private $rootScope: IScope;
  private $uibModal: IModalService;
  private $state: StateService;
  private overrideRegistry: OverrideRegistry;
  private cacheInitializer: CacheInitializerService;

  constructor(props: IInsightMenuProps) {
    super(props);
    this.state = {} as IInsightMenuState;
    this.$state = ReactInjector.$state;
    this.$uibModal = ReactInjector.modalService;
    this.$rootScope = ReactInjector.$rootScope;
    this.overrideRegistry = ReactInjector.overrideRegistry;
    this.cacheInitializer = ReactInjector.cacheInitializer;
  }

  private createProject() {
    this.$uibModal
      .open({
        scope: this.$rootScope.$new(),
        templateUrl: require('../projects/configure/configureProject.modal.html'),
        controller: 'ConfigureProjectModalCtrl',
        controllerAs: 'ctrl',
        size: 'lg',
        resolve: {
          projectConfig: () => {
            return {};
          },
        },
      })
      .result.then(this.routeToProject)
      .catch(() => {});
  }

  private routeToProject(project: IProject) {
    this.$state.go('home.project.dashboard', { project: project.name });
  }

  private createApplication() {
    this.$uibModal
      .open({
        scope: this.$rootScope.$new(),
        templateUrl: this.overrideRegistry.getTemplate(
          'createApplicationModal',
          require('../application/modal/newapplication.html'),
        ),
        controller: this.overrideRegistry.getController('CreateApplicationModalCtrl'),
        controllerAs: 'newAppModal',
      })
      .result.then(this.routeToApplication)
      .catch(() => {});
  }

  private routeToApplication(app: Application) {
    this.$state.go('home.applications.application.insight.clusters', { application: app.name });
  }

  private refreshAllCaches() {
    if (this.state.refreshingCache) {
      return;
    }

    this.setState({ refreshingCache: true });
    this.cacheInitializer.refreshCaches().then(() => {
      this.setState({ refreshingCache: false });
    });
  }

  public render() {
    const { createApp, createProject, refreshCaches } = this.props;
    const refreshMarkup = this.state.refreshingCache ? (
      <span>
        <span className="fa fa-sync-alt fa-spin" /> Refreshing...
      </span>
    ) : (
      <span>Refresh all caches</span>
    );

    return (
      <DropdownButton pullRight={true} title="Actions" id="insight-menu">
        {createApp && (
          <MenuItem href="javascript:void(0)" onClick={this.createApplication}>
            Create Application
          </MenuItem>
        )}
        {createProject && (
          <MenuItem href="javascript:void(0)" onClick={this.createProject}>
            Create Project
          </MenuItem>
        )}
        {refreshCaches && (
          <MenuItem href="javascript:void(0)" onClick={this.refreshAllCaches}>
            {refreshMarkup}
          </MenuItem>
        )}
      </DropdownButton>
    );
  }
}
