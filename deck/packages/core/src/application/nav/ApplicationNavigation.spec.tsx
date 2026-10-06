import { render, screen } from '@testing-library/react';
import { StateMatcher } from '@uirouter/core';
import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact } from '@uirouter/react';
import React from 'react';
import { RecoilRoot } from 'recoil';

import {
  mockAppConfigDataSourceConfig,
  mockLoadBalancerDataSourceConfig,
  mockPipelineDataSourceConfig,
  mockServerGroupDataSourceConfig,
  mockTaskDataSourceConfig,
} from '@spinnaker/mocks';

import { ApplicationNavigation } from './ApplicationNavigation';
import { ApplicationModelBuilder } from '../../application';
import { SETTINGS } from '../../config';
import type { IPipeline } from '../../domain';
import type { ApplicationDataSource } from '../service/applicationDataSource';

describe('ApplicationNavigation', () => {
  let router: UIRouterReact;
  let originalPagerDuty: boolean;
  const currentStates = ['**.pipelines.**', '**.tasks.**'];

  beforeEach(() => {
    originalPagerDuty = SETTINGS.feature.pagerDuty;
    router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    // Initialize current route
    vi.spyOn(router.stateService, 'includes').mockImplementation((substate: any) => currentStates.includes(substate));
    vi.spyOn(StateMatcher.prototype, 'find').mockImplementation(() => undefined as any);
  });

  afterEach(() => {
    SETTINGS.feature.pagerDuty = originalPagerDuty;
    router.dispose();
  });

  const renderNavigation = (app: ReturnType<typeof ApplicationModelBuilder.createApplicationForTests>) =>
    render(
      <RecoilRoot>
        <UIRouterContext.Provider value={router}>
          <ApplicationNavigation app={app} />
        </UIRouterContext.Provider>
      </RecoilRoot>,
    );

  it('should render header, categories', () => {
    const app = ApplicationModelBuilder.createApplicationForTests(
      'testapp',
      mockPipelineDataSourceConfig,
      mockServerGroupDataSourceConfig,
      mockAppConfigDataSourceConfig,
    );
    const activeDataSource = app.getDataSource('executions');
    app.dataSources.push({ ...activeDataSource, key: 'runningExecutions' } as ApplicationDataSource<IPipeline>);
    app.getDataSource(activeDataSource.badge).status$.next({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 0,
      error: null,
      data: [mockPipelineDataSourceConfig, mockPipelineDataSourceConfig],
    });
    app.attributes.dataSources = app.dataSources;

    app.setActiveState(activeDataSource);

    const { container } = renderNavigation(app);

    expect(container.querySelector('.nav-header')).toBeInTheDocument();
    expect(container.querySelectorAll('.nav-section')).toHaveLength(3);
    expect(container.querySelector('.page-category')).not.toBeInTheDocument();
  });

  it('renders nav routes with shared flex row classes', () => {
    const app = ApplicationModelBuilder.createApplicationForTests('testapp', mockServerGroupDataSourceConfig);
    app.attributes.dataSources = app.dataSources;

    const { container } = renderNavigation(app);
    const firstNavRoute = container.querySelector('a.nav-category');

    expect(firstNavRoute).toHaveClass('flex-container-h', 'middle');
  });

  it('should render pager button', () => {
    SETTINGS.feature.pagerDuty = true;
    const app = ApplicationModelBuilder.createApplicationForTests('testapp');
    app.attributes.pdApiKey = 'fake-api-key';

    const { container } = renderNavigation(app);

    expect(container.querySelectorAll('.page-category')).toHaveLength(1);
    expect(screen.getByText(/page app owner/i)).toBeInTheDocument();
  });

  it('should not render any categories if none configured', () => {
    const app = ApplicationModelBuilder.createApplicationForTests('testapp');

    const { container } = renderNavigation(app);

    expect(container.querySelectorAll('.nav-content .nav-section')).toHaveLength(0);
  });

  it('sets active category', () => {
    const app = ApplicationModelBuilder.createApplicationForTests(
      'testapp',
      mockServerGroupDataSourceConfig,
      mockLoadBalancerDataSourceConfig,
      mockTaskDataSourceConfig,
      mockAppConfigDataSourceConfig,
    );
    const activeDataSource = app.getDataSource('tasks');
    app.dataSources.push({ ...activeDataSource, key: 'runningTasks' } as ApplicationDataSource<IPipeline>);
    app.getDataSource(activeDataSource.badge).status$.next({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 0,
      error: null,
      data: [],
    });
    app.attributes.dataSources = app.dataSources;
    app.setActiveState(activeDataSource);

    const { container } = renderNavigation(app);
    const taskAndConfigSection = container.querySelectorAll('.nav-section')[1] as HTMLElement;

    const [taskRoute, configRoute] = Array.from(taskAndConfigSection.querySelectorAll('a.nav-category'));

    expect(taskRoute).toHaveTextContent('Tasks');
    expect(taskRoute).toHaveClass('active');
    expect(configRoute).toHaveTextContent('Config');
    expect(configRoute).not.toHaveClass('active');
  });
});
