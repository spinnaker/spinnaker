import React from 'react';
import { RecoilRoot } from 'recoil';
import { mount } from 'enzyme';
import { mock } from 'angular';
import { UIRouterReact, UIRouterContext } from '@uirouter/react';
import { StateMatcher } from '@uirouter/core';

import { REACT_MODULE } from '../../reactShims';
import { OVERRIDE_REGISTRY } from '../../overrideRegistry';
import {
  mockAppConfigDataSourceConfig,
  mockServerGroupDataSourceConfig,
  mockLoadBalancerDataSourceConfig,
  mockTaskDataSourceConfig,
  mockPipelineDataSourceConfig,
} from '@spinnaker/mocks';
import { ApplicationModelBuilder } from '../../application';
import { IPipeline } from '../../domain';
import { ApplicationDataSource } from '../service/applicationDataSource';

import { ApplicationNavigation } from './ApplicationNavigation';
import { SETTINGS } from '../../config';

describe('ApplicationNavigation', () => {
  let $uiRouter: UIRouterReact;
  const currentStates = ['**.pipelines.**', '**.tasks.**'];

  beforeEach(mock.module(REACT_MODULE, OVERRIDE_REGISTRY));
  beforeEach(
    mock.inject((_$uiRouter_: UIRouterReact) => {
      $uiRouter = _$uiRouter_;
    }),
  );
  beforeEach(() => {
    // Initialize current route
    spyOn($uiRouter.stateService, 'includes').and.callFake((substate: any) => currentStates.includes(substate));
    spyOn(StateMatcher.prototype, 'find').and.callFake(() => undefined as any);
  });

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

    const wrapper = mount(
      <RecoilRoot>
        <UIRouterContext.Provider value={$uiRouter}>
          <ApplicationNavigation app={app} />
        </UIRouterContext.Provider>
      </RecoilRoot>,
    );

    const header = wrapper.find('.nav-header');
    expect(header.length).toEqual(1);

    const navSections = wrapper.find('NavSection');
    expect(navSections.length).toEqual(3);

    const pagerDutyButton = wrapper.find('.page-category');
    expect(pagerDutyButton.length).toEqual(0);
  });

  it('should render pager button', () => {
    SETTINGS.feature.pagerDuty = true;
    const app = ApplicationModelBuilder.createApplicationForTests('testapp');
    app.attributes.pdApiKey = 'fake-api-key';

    const wrapper = mount(
      <RecoilRoot>
        <UIRouterContext.Provider value={$uiRouter}>
          <ApplicationNavigation app={app} />
        </UIRouterContext.Provider>
      </RecoilRoot>,
    );

    const pagerDutyButton = wrapper.find('.page-category');
    expect(pagerDutyButton.length).toEqual(1);
  });

  it('should not render any categories if none configured', () => {
    const app = ApplicationModelBuilder.createApplicationForTests('testapp');

    const wrapper = mount(
      <RecoilRoot>
        <UIRouterContext.Provider value={$uiRouter}>
          <ApplicationNavigation app={app} />
        </UIRouterContext.Provider>
      </RecoilRoot>,
    );

    const navSection = wrapper.find('NavSection');
    expect(navSection.length).toEqual(0);
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

    const wrapper = mount(
      <RecoilRoot>
        <UIRouterContext.Provider value={$uiRouter}>
          <ApplicationNavigation app={app} />
        </UIRouterContext.Provider>
      </RecoilRoot>,
    );

    const navSection = wrapper.find('NavSection').at(1);
    const taskRoute = navSection.find('NavRoute').at(0);

    const taskCategory = taskRoute.find('NavItem');
    const isTaskCategoryActive = taskCategory.prop('isActive');
    expect(isTaskCategoryActive).toEqual(true);

    const configRoute = navSection.find('NavRoute').at(1);
    const configCategory = configRoute.find('NavItem');
    const isConfigCategoryActive = configCategory.prop('isActive');
    expect(isConfigCategoryActive).toEqual(false);
  });
});
