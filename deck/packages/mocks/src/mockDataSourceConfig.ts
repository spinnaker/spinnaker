import { $q } from 'ngimport';
import type { ILoadBalancer, IPipeline, ISecurityGroup, IServerGroup } from '@spinnaker/core';
import type { Application, IDataSourceConfig, ITask } from '@spinnaker/core';
import { mockLoadBalancer } from './mockLoadBalancer';
import { mockPipeline } from './mockPipeline';
import { mockSecurityGroup } from './mockSecurityGroup';
import { mockServerGroup } from './mockServerGroup';
import { createMockTask } from './mockTask';

export const mockLoadBalancerDataSourceConfig: IDataSourceConfig<ILoadBalancer> = {
  key: 'loadBalancers',
  sref: '.insight.loadBalancers',
  category: 'infrastructure',
  optional: true,
  loader: (application: Application) => $q(() => application),
  onLoad: (application: Application) => $q(() => application),
  afterLoad: (application: Application) => application,
  providerField: 'cloudProvider',
  credentialsField: 'account',
  regionField: 'region',
  description: 'Traffic distribution management between servers',
  defaultData: mockLoadBalancer,
};

export const mockPipelineDataSourceConfig: IDataSourceConfig<IPipeline> = {
  optional: true,
  primary: true,
  key: 'executions',
  label: 'Pipelines',
  category: 'delivery',
  sref: '.pipelines.executions',
  activeState: '**.pipelines.**',
  loader: (application: Application) => $q(() => application),
  onLoad: (application: Application) => $q(() => application),
  afterLoad: (application: Application) => application,
  lazy: true,
  badge: 'runningExecutions',
  description: 'Orchestrated deployment management',
  defaultData: mockPipeline,
};

export const mockSecurityGroupDataSourceConfig: IDataSourceConfig<ISecurityGroup> = {
  key: 'securityGroups',
  label: 'Firewalls',
  category: 'infrastructure',
  sref: '.insight.firewalls',
  optional: true,
  loader: (application: Application) => $q(() => application),
  onLoad: (application: Application) => $q(() => application),
  afterLoad: (application: Application) => application,
  providerField: 'provider',
  credentialsField: 'accountName',
  regionField: 'region',
  description: 'Network traffic access management',
  defaultData: mockSecurityGroup,
};

export const mockServerGroupDataSourceConfig: IDataSourceConfig<IServerGroup> = {
  key: 'serverGroups',
  label: 'Clusters',
  category: 'infrastructure',
  sref: '.insight.clusters',
  optional: true,
  primary: true,
  loader: (application: Application) => $q(() => application),
  onLoad: (application: Application) => $q(() => application),
  afterLoad: (application: Application) => application,
  providerField: 'type',
  credentialsField: 'account',
  regionField: 'region',
  description: 'Collections of server groups or jobs',
  defaultData: mockServerGroup,
};

export const mockTaskDataSourceConfig: IDataSourceConfig<ITask> = {
  key: 'tasks',
  sref: '.tasks',
  badge: 'runningTasks',
  category: 'tasks',
  loader: (application: Application) => $q(() => application),
  onLoad: (application: Application) => $q(() => application),
  afterLoad: (application: Application) => application,
  lazy: true,
  primary: true,
  icon: 'fa fa-sm fa-fw fa-check-square',
  defaultData: createMockTask('SUCCEEDED'),
};

export const mockAppConfigDataSourceConfig: IDataSourceConfig<any> = {
  key: 'config',
  label: 'Config',
  sref: '.config',
  defaultData: [],
};
