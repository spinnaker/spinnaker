import { act, render } from '@testing-library/react';
import React from 'react';
import { RecoilRoot } from 'recoil';
import { BehaviorSubject } from 'rxjs';

import { mockEntityTags, mockPipelineDataSourceConfig, mockServerGroupDataSourceConfig } from '@spinnaker/mocks';

import { NavItem } from './NavItem';
import type { Application } from '../../application';
import { ApplicationModelBuilder } from '../../application';
import type { IPipeline, IServerGroup } from '../../domain';
import type { ApplicationDataSource, IDataSourceConfig } from '../service/applicationDataSource';

describe('NavItem', () => {
  const buildApp = <T,>(config: IDataSourceConfig<T>): Application =>
    ApplicationModelBuilder.createApplicationForTests('testapp', config);

  it('should render a datasources icon', () => {
    const app = buildApp<IServerGroup>(mockServerGroupDataSourceConfig);
    const dataSource = app.getDataSource('serverGroups');
    dataSource.iconName = 'spMenuClusters';

    const { container } = render(
      <RecoilRoot>
        <NavItem app={app} dataSource={dataSource} isActive={false} />
      </RecoilRoot>,
    );
    expect(container.querySelectorAll('svg')).toHaveLength(1);
  });

  it('should render a placeholder when there is icon', () => {
    const app = buildApp<IServerGroup>(mockServerGroupDataSourceConfig);
    const dataSource = app.getDataSource('serverGroups');

    const { container } = render(
      <RecoilRoot>
        <NavItem app={app} dataSource={dataSource} isActive={false} />
      </RecoilRoot>,
    );
    expect(container.querySelectorAll('svg')).toHaveLength(0);
  });

  it('should render running tasks badge', () => {
    const app = buildApp<IPipeline>(mockPipelineDataSourceConfig);
    const dataSource = app.getDataSource('executions');
    app.dataSources.push({ ...dataSource, key: 'runningExecutions' } as ApplicationDataSource<IPipeline>);
    app.getDataSource(dataSource.badge).status$ = new BehaviorSubject({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 0,
      error: null,
      data: [mockPipelineDataSourceConfig, mockPipelineDataSourceConfig],
    });

    const { container } = render(
      <RecoilRoot>
        <NavItem app={app} dataSource={dataSource} isActive={false} />
      </RecoilRoot>,
    );
    expect(container.querySelector('.badge-running-count')).toHaveTextContent('2');
    expect(container.querySelector('.badge-none')).not.toBeInTheDocument();
  });

  it('should not render running tasks badge if there are none', () => {
    const app = buildApp<IPipeline>(mockPipelineDataSourceConfig);
    const dataSource = app.getDataSource('executions');
    app.dataSources.push({ ...dataSource, key: 'runningExecutions' } as ApplicationDataSource<IPipeline>);

    const { container } = render(
      <RecoilRoot>
        <NavItem app={app} dataSource={dataSource} isActive={false} />
      </RecoilRoot>,
    );
    expect(container.querySelector('.badge-running-count')).not.toBeInTheDocument();
    expect(container.querySelector('.badge-none')).toBeEmptyDOMElement();
  });

  it('subscribes to runningCount updates', () => {
    const app = buildApp<IPipeline>(mockPipelineDataSourceConfig);
    const dataSource = app.getDataSource('executions');
    app.dataSources.push({ ...dataSource, key: 'runningExecutions' } as ApplicationDataSource<IPipeline>);

    const { container, rerender } = render(
      <RecoilRoot>
        <NavItem app={app} dataSource={dataSource} isActive={false} />
      </RecoilRoot>,
    );
    expect(container.querySelector('.badge-running-count')).not.toBeInTheDocument();
    expect(container.querySelector('.badge-none')).toBeEmptyDOMElement();

    const updatedApp = buildApp<IPipeline>(mockPipelineDataSourceConfig);
    updatedApp.dataSources.push({
      ...dataSource,
      key: 'runningExecutions',
    } as ApplicationDataSource<IPipeline>);
    updatedApp.getDataSource(dataSource.badge).status$ = new BehaviorSubject({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 0,
      error: null,
      data: [mockPipelineDataSourceConfig, mockPipelineDataSourceConfig],
    });

    rerender(
      <RecoilRoot>
        <NavItem app={updatedApp} dataSource={dataSource} isActive={false} />
      </RecoilRoot>,
    );
    expect(container.querySelector('.badge-running-count')).toHaveTextContent('2');
    expect(container.querySelector('.badge-none')).not.toBeInTheDocument();
  });

  it('should subscribe to alert updates', () => {
    const app = buildApp<IServerGroup>(mockServerGroupDataSourceConfig);
    const dataSource = app.getDataSource('serverGroups');
    const initialObservers = dataSource.status$.observers.length;
    const first = render(
      <RecoilRoot>
        <NavItem app={app} dataSource={dataSource} isActive={false} />
      </RecoilRoot>,
    );
    expect(dataSource.status$.observers.length).toBeGreaterThan(initialObservers);

    dataSource.alerts = [mockEntityTags];
    dataSource.entityTags = [mockEntityTags];
    act(() => dataSource.status$.next({ ...dataSource.status$.value }));
    first.unmount();
    expect(dataSource.status$.observers).toHaveLength(initialObservers);
  });
});
