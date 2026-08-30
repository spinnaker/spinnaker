import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';
import { RecentHistoryService } from '../../history/recentHistory.service';
import { UrlBuilder } from '../../navigation';
import { PipelineConfigService } from '../../pipeline/config/services/PipelineConfigService';
import { mountAndFlush } from '../../utils/testUtils';
import { ProjectDashboard } from './ProjectDashboard';
import { ProjectReader } from '../service/ProjectReader';

const project = {
  id: 'kubernetesproject',
  name: 'kubernetesproject',
  email: 'team@example.com',
  notFound: false,
  config: {
    applications: ['kubernetesapp'],
    clusters: [{ account: 'k8s-local', stack: '*', detail: '*', applications: ['kubernetesapp'] }],
    pipelineConfigs: [{ application: 'kubernetesapp', pipelineConfigId: 'deployment' }],
  },
} as any;

const cluster = {
  account: 'k8s-local',
  stack: '*',
  detail: '*',
  instanceCounts: { total: 8, up: 8, down: 0, unknown: 0, outOfService: 0, starting: 0 },
  applications: [
    {
      application: 'kubernetesapp',
      lastPush: Date.now() - 60_000,
      clusters: [{ region: 'dev', builds: [{ images: ['nginx'] }], instanceCounts: { total: 8, up: 8 } }],
    },
  ],
} as any;

const execution = {
  id: '01',
  application: 'kubernetesapp',
  name: 'deployment',
  pipelineConfigId: 'deployment',
  trigger: {},
  hydrated: true,
  startTime: Date.now() - 60_000,
  stageSummaries: [
    {
      id: '1',
      refId: '1',
      index: 0,
      name: 'Deploy',
      type: 'deployManifest',
      status: 'SUCCEEDED',
      runningTimeInMs: 60_000,
      stages: [],
      labelComponent: () => <span>Deploy</span>,
      markerIcon: () => null,
      suspendedStageTypes: new Set(),
    },
  ],
} as any;

const taggedPipeline = {
  id: 'tagged-deployment',
  application: 'storefront',
  name: 'Storefront deploy',
  tags: [{ name: 'project', value: 'kubernetesproject' }],
  stages: [],
  triggers: [],
  parameterConfig: [],
  limitConcurrent: true,
  keepWaitingPipelines: false,
} as any;

const transition = (params: any = {}) =>
  ({
    params: () => params,
    router: {
      stateService: {
        go: jasmine.createSpy('go'),
      },
    },
  } as any);

describe('<ProjectDashboard />', () => {
  let executionService: { getProjectExecutions: jasmine.Spy; getProjectExecutionsForConfigIds: jasmine.Spy };
  const TestDashboard = (props: React.ComponentProps<typeof ProjectDashboard>) => (
    <DeckRuntimeContext.Provider value={{ services: { executionService } } as any}>
      <ProjectDashboard {...props} />
    </DeckRuntimeContext.Provider>
  );

  beforeEach(() => {
    spyOn(RecentHistoryService, 'addExtraDataToLatest').and.stub();
    spyOn(RecentHistoryService, 'removeLastItem').and.stub();
    spyOn(UrlBuilder, 'buildFromMetadata').and.callFake((metadata: any) => {
      const reg = metadata.region ? `?reg=${metadata.region}` : '';
      return `#/projects/${metadata.project}/applications/${metadata.application}/clusters${reg}`;
    });
    spyOn(ProjectReader, 'getProjectClusters').and.returnValue(Promise.resolve([cluster]));
    spyOn(PipelineConfigService, 'getAllPipelineConfigs').and.returnValue(
      Promise.resolve([
        { ...taggedPipeline },
        { ...taggedPipeline, id: 'deployment', application: 'kubernetesapp', name: 'Deployment', tags: [] },
      ]),
    );
    executionService = {
      getProjectExecutions: jasmine.createSpy('getProjectExecutions').and.returnValue(Promise.resolve([execution])),
      getProjectExecutionsForConfigIds: jasmine
        .createSpy('getProjectExecutionsForConfigIds')
        .and.returnValue(Promise.resolve([execution])),
    };
  });

  it('loads clusters and executions and renders dashboard columns', async () => {
    const wrapper = await mountAndFlush(<TestDashboard projectConfiguration={project} transition={transition()} />);

    expect(RecentHistoryService.addExtraDataToLatest).toHaveBeenCalledWith('projects', {
      config: { applications: ['kubernetesapp'] },
    });
    expect(ProjectReader.getProjectClusters).toHaveBeenCalledWith('kubernetesproject');
    expect(PipelineConfigService.getAllPipelineConfigs).toHaveBeenCalled();
    expect(executionService.getProjectExecutionsForConfigIds).toHaveBeenCalledWith(['deployment', 'tagged-deployment']);
    expect(wrapper.find('.project-dashboard').exists()).toBe(true);
    expect(wrapper.find('h3').at(0).text()).toContain('Application Status');
    expect(wrapper.find('ProjectCluster').length).toBe(1);
    expect(wrapper.find('.project-pipeline-group').length).toBe(2);
    expect(wrapper.find('ProjectPipeline').length).toBe(1);
    expect(wrapper.find('.project-pipeline').length).toBe(1);
    expect(wrapper.text()).toContain('Never run');
    expect(wrapper.find('project-pipeline').exists()).toBe(false);

    wrapper.unmount();
  });

  it('skips cluster request and renders empty states when nothing is configured', async () => {
    (ProjectReader.getProjectClusters as jasmine.Spy).calls.reset();
    (PipelineConfigService.getAllPipelineConfigs as jasmine.Spy).and.returnValue(Promise.resolve([]));
    const emptyProject = {
      ...project,
      config: { applications: [], clusters: [], pipelineConfigs: [] },
    };

    const wrapper = await mountAndFlush(
      <TestDashboard projectConfiguration={emptyProject} transition={transition()} />,
    );

    expect(ProjectReader.getProjectClusters).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('No clusters configured');
    expect(wrapper.text()).toContain('No pipelines found');

    wrapper.unmount();
  });

  it('renders independent cluster and execution load errors', async () => {
    (ProjectReader.getProjectClusters as jasmine.Spy).and.returnValue(Promise.reject(new Error('clusters failed')));
    executionService.getProjectExecutionsForConfigIds.and.returnValue(Promise.reject(new Error('executions failed')));

    const wrapper = await mountAndFlush(<TestDashboard projectConfiguration={project} transition={transition()} />);

    expect(wrapper.text()).toContain('There was a problem loading the clusters for this project.');
    expect(wrapper.text()).toContain('There was a problem loading the executions for this project.');

    wrapper.unmount();
  });

  it('falls back to manual project executions when config discovery fails', async () => {
    (PipelineConfigService.getAllPipelineConfigs as jasmine.Spy).and.returnValue(
      Promise.reject(new Error('configs failed')),
    );

    const wrapper = await mountAndFlush(<TestDashboard projectConfiguration={project} transition={transition()} />);

    expect(executionService.getProjectExecutions).toHaveBeenCalledWith('kubernetesproject');
    expect(wrapper.find('ProjectPipeline').length).toBe(1);
    expect(wrapper.text()).toContain('Automatic pipeline discovery is unavailable');
    wrapper.unmount();
  });

  it('shows an execution error instead of never-run rows when selected execution loading fails', async () => {
    executionService.getProjectExecutionsForConfigIds.and.returnValue(Promise.reject(new Error('executions failed')));

    const wrapper = await mountAndFlush(<TestDashboard projectConfiguration={project} transition={transition()} />);

    expect(wrapper.text()).toContain('There was a problem loading the executions for this project.');
    expect(wrapper.text()).not.toContain('Never run');
    expect(executionService.getProjectExecutions).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('renders an empty state when no manual or tagged pipelines exist', async () => {
    (PipelineConfigService.getAllPipelineConfigs as jasmine.Spy).and.returnValue(Promise.resolve([]));
    const emptyProject = { ...project, config: { applications: [], clusters: [], pipelineConfigs: [] } };

    const wrapper = await mountAndFlush(
      <TestDashboard projectConfiguration={emptyProject} transition={transition()} />,
    );

    expect(executionService.getProjectExecutionsForConfigIds).toHaveBeenCalledWith([]);
    expect(wrapper.text()).toContain('No pipelines found');
    wrapper.unmount();
  });

  it('toggles region filters and replaces the current route params', async () => {
    const tx = transition({ reg: { dev: true } });
    const wrapper = await mountAndFlush(<TestDashboard projectConfiguration={project} transition={tx} />);

    wrapper.find('RegionFilter h6.dropdown-toggle').simulate('click');
    await act(async () => {
      wrapper.find('RegionFilter li').first().simulate('click');
    });
    wrapper.update();

    expect(tx.router.stateService.go).toHaveBeenCalledWith('.', { reg: {} }, { location: 'replace' });

    wrapper.unmount();
  });

  it('renders nothing for missing projects and removes recent history', () => {
    const wrapper = mount(
      <TestDashboard projectConfiguration={{ ...project, notFound: true }} transition={transition()} />,
    );

    expect(RecentHistoryService.removeLastItem).toHaveBeenCalledWith('projects');
    expect(wrapper.find('.project-dashboard').exists()).toBe(false);

    wrapper.unmount();
  });
});
