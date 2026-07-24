import type { StateService } from '@uirouter/core';
import React from 'react';
import { Button } from 'react-bootstrap';

import { CreateApplicationModal } from '../application/modal/CreateApplicationModal';
import type { IDeckRuntimeServicesInjectedProps } from '../bootstrap/DeckRuntimeContext';
import { withDeckRuntimeServices } from '../bootstrap/DeckRuntimeContext';
import type { CacheInitializerService } from '../cache';
import type { IRouterInjectedProps } from '../navigation/routerContext';
import { withRouter } from '../navigation/routerContext';
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

export class InsightMenuComponent extends React.Component<
  IInsightMenuProps & IRouterInjectedProps & IDeckRuntimeServicesInjectedProps,
  IInsightMenuState
> {
  public static defaultProps: IInsightMenuProps = { createApp: true, createProject: true, refreshCaches: true };

  private $state: StateService;
  private cacheInitializer: CacheInitializerService;

  constructor(props: IInsightMenuProps & IRouterInjectedProps & IDeckRuntimeServicesInjectedProps) {
    super(props);
    this.state = {} as IInsightMenuState;
    this.$state = props.stateService;
    this.cacheInitializer = props.deckRuntimeServices.cacheInitializer;
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

const OverridableInsightMenu = Overridable('createInsightMenu')(InsightMenuComponent);
export const InsightMenu = withDeckRuntimeServices(withRouter(OverridableInsightMenu));
InsightMenu.displayName = 'InsightMenu';
