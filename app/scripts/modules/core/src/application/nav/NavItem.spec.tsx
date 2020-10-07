import React from 'react';
import { RecoilRoot } from 'recoil';
import { mount } from 'enzyme';
import { BehaviorSubject } from 'rxjs';

import { mockEntityTags, mockServerGroupDataSourceConfig, mockPipelineDataSourceConfig } from '@spinnaker/mocks';
import { Application, ApplicationModelBuilder } from '../../application';
import { ApplicationDataSource, IDataSourceConfig } from '../service/applicationDataSource';
import { IEntityTags, IServerGroup, IPipeline } from '../../domain';
import { NavItem } from './NavItem';

describe('NavItem', () => {
  const buildApp = <T,>(config: IDataSourceConfig<T>): Application =>
    ApplicationModelBuilder.createApplicationForTests('testapp', config);

  it('should render a datasources icon', () => {
    const app = buildApp<IServerGroup>(mockServerGroupDataSourceConfig);
    const dataSource = app.getDataSource('serverGroups');
    dataSource.iconName = 'spMenuClusters';

    const wrapper = mount(
      <RecoilRoot>
        <NavItem app={app} dataSource={dataSource} isActive={false} />
      </RecoilRoot>,
    );
    const nodes = wrapper.children();
    expect(nodes.find('svg').length).toEqual(1);
  });

  it('should render a placeholder when there is icon', () => {
    const app = buildApp<IServerGroup>(mockServerGroupDataSourceConfig);
    const dataSource = app.getDataSource('serverGroups');

    const wrapper = mount(
      <RecoilRoot>
        <NavItem app={app} dataSource={dataSource} isActive={false} />
      </RecoilRoot>,
    );
    const nodes = wrapper.children();
    expect(nodes.find('svg').length).toEqual(0);
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

    const wrapper = mount(
      <RecoilRoot>
        <NavItem app={app} dataSource={dataSource} isActive={false} />
      </RecoilRoot>,
    );
    const nodes = wrapper.children();
    expect(nodes.find('.badge-running-count').length).toBe(1);
    expect(nodes.find('.badge-none').length).toBe(0);

    const text = nodes.find('.badge-running-count').getDOMNode();
    expect(text.textContent).toBe('2');
  });

  it('should not render running tasks badge if there are none', () => {
    const app = buildApp<IPipeline>(mockPipelineDataSourceConfig);
    const dataSource = app.getDataSource('executions');
    app.dataSources.push({ ...dataSource, key: 'runningExecutions' } as ApplicationDataSource<IPipeline>);

    const wrapper = mount(
      <RecoilRoot>
        <NavItem app={app} dataSource={dataSource} isActive={false} />
      </RecoilRoot>,
    );
    const nodes = wrapper.children();
    expect(nodes.find('.badge-running-count').length).toBe(0);
    expect(nodes.find('.badge-none').length).toBe(1);

    const text = nodes.find('.badge-none').getDOMNode();
    expect(text.textContent).toBe('');
  });

  it('subscribes to runningCount updates', () => {
    const app = buildApp<IPipeline>(mockPipelineDataSourceConfig);
    const dataSource = app.getDataSource('executions');
    app.dataSources.push({ ...dataSource, key: 'runningExecutions' } as ApplicationDataSource<IPipeline>);

    const wrapper = mount(
      <RecoilRoot>
        <NavItem app={app} dataSource={dataSource} isActive={false} />
      </RecoilRoot>,
    );
    const nodes = wrapper.children();
    expect(nodes.find('.badge-running-count').length).toBe(0);
    expect(nodes.find('.badge-none').length).toBe(1);

    const text = nodes.find('.badge-none').getDOMNode();
    expect(text.textContent).toBe('');

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

    wrapper.setProps({ children: <NavItem app={updatedApp} dataSource={dataSource} isActive={false} /> });
    wrapper.update();

    const newNodes = wrapper.children();
    expect(newNodes.find('.badge-running-count').length).toBe(1);
    expect(newNodes.find('.badge-none').length).toBe(0);

    const newText = newNodes.find('.badge-running-count').getDOMNode();
    expect(newText.textContent).toBe('2');
  });

  it('should subscribe to alert updates', () => {
    const app = buildApp<IServerGroup>(mockServerGroupDataSourceConfig);
    const dataSource = app.getDataSource('serverGroups');
    const wrapper = mount(
      <RecoilRoot>
        <NavItem app={app} dataSource={dataSource} isActive={false} />
      </RecoilRoot>,
    );
    const nodes = wrapper.children();
    const tags: IEntityTags[] = nodes.find('DataSourceNotifications').prop('tags');
    expect(tags.length).toEqual(0);

    dataSource.alerts = [mockEntityTags];
    dataSource.entityTags = [mockEntityTags];
    wrapper.setProps({ children: <NavItem app={app} dataSource={dataSource} isActive={false} /> });
    wrapper.update();

    const newTags: IEntityTags[] = wrapper.children().find('DataSourceNotifications').prop('tags');
    expect(newTags.length).toEqual(1);
  });
});
