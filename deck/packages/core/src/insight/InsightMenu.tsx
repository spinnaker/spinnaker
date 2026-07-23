import type { StateService } from '@uirouter/core';
import React from 'react';
import { Button } from 'react-bootstrap';
import { AngularServices } from '../angular/services';

import { CreateApplicationModal } from '../application/modal/CreateApplicationModal';
import type { CacheInitializerService } from '../cache';
import { Overridable } from '../overrideRegistry';
import { ConfigureProjectModal } from '../projects';

export interface IInsightMenuProps {
  createApp?: boolean;
  createProject?: boolean;
  refreshCaches?: boolean;
}

export interface IInsightMenuState {
  refreshingCache: boolean;
}

@Overridable('createInsightMenu')
export class InsightMenu extends React.Component<IInsightMenuProps, IInsightMenuState> {
  public static defaultProps: IInsightMenuProps = { createApp: true, createProject: true, refreshCaches: true };

  private $state: StateService;
  private cacheInitializer: CacheInitializerService;

  constructor(props: IInsightMenuProps) {
    super(props);
    this.state = {} as IInsightMenuState;
    this.$state = AngularServices.$state;
    this.cacheInitializer = AngularServices.cacheInitializer;
  }

  private createProject = () =>
    ConfigureProjectModal.show()
      .then((result) => {
        this.$state.go('home.project.dashboard', { project: result.name });
      })
      .catch(() => {});

  private createApplication = () =>
    CreateApplicationModal.show()
      .then(this.routeToApplication)
      .catch(() => {});

  private routeToApplication = (app: { name: string }) => {
    this.$state.go('home.applications.application', { application: app.name });
  };

  private refreshAllCaches = () => {
    if (this.state.refreshingCache) {
      return;
    }

    this.setState({ refreshingCache: true });
    this.cacheInitializer.refreshCaches().then(() => {
      this.setState({ refreshingCache: false });
    });
  };

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
      <div id="insight-menu">
        {createProject && (
          <Button
            bsStyle={createApp ? 'default' : 'primary'}
            href="javascript:void(0)"
            onClick={this.createProject}
            style={{ marginRight: createApp ? '5px' : '' }}
          >
            Create Project
          </Button>
        )}

        {createApp && (
          <Button
            bsStyle="primary"
            href="javascript:void(0)"
            onClick={this.createApplication}
            style={{ marginRight: refreshCaches ? '5px' : '' }}
          >
            Create Application
          </Button>
        )}

        {refreshCaches && (
          <Button href="javascript:void(0)" onClick={this.refreshAllCaches}>
            {refreshMarkup}
          </Button>
        )}
      </div>
    );
  }
}
