import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';
import { RecentHistoryService } from '../../history/recentHistory.service';
import { UrlBuilder } from '../../navigation';
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
  let executionService: { getProjectExecutions: jasmine.Spy };
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
    executionService = {
      getProjectExecutions: jasmine.createSpy('getProjectExecutions').and.returnValue(Promise.resolve([execution])),
    };
  });

  it('loads clusters and executions and renders dashboard columns', async () => {
    const wrapper = await mountAndFlush(<TestDashboard projectConfiguration={project} transition={transition()} />);

    expect(RecentHistoryService.addExtraDataToLatest).toHaveBeenCalledWith('projects', {
      config: { applications: ['kubernetesapp'] },
    });
    expect(ProjectReader.getProjectClusters).toHaveBeenCalledWith('kubernetesproject');
    expect(executionService.getProjectExecutions).toHaveBeenCalledWith('kubernetesproject');
    expect(wrapper.find('.project-dashboard').exists()).toBe(true);
    expect(wrapper.find('h3').at(0).text()).toContain('Application Status');
    expect(wrapper.find('ProjectCluster').length).toBe(1);
    expect(wrapper.find('ProjectPipeline').length).toBe(1);

    wrapper.unmount();
  });

  it('skips cluster request and renders empty states when nothing is configured', async () => {
    (ProjectReader.getProjectClusters as jasmine.Spy).calls.reset();
    const emptyProject = {
      ...project,
      config: { applications: [], clusters: [], pipelineConfigs: [] },
    };

    const wrapper = await mountAndFlush(
      <TestDashboard projectConfiguration={emptyProject} transition={transition()} />,
    );

    expect(ProjectReader.getProjectClusters).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('No clusters configured');
    expect(wrapper.text()).toContain('No pipelines configured');

    wrapper.unmount();
  });

  it('renders independent cluster and execution load errors', async () => {
    (ProjectReader.getProjectClusters as jasmine.Spy).and.returnValue(Promise.reject(new Error('clusters failed')));
    executionService.getProjectExecutions.and.returnValue(Promise.reject(new Error('executions failed')));

    const wrapper = await mountAndFlush(<TestDashboard projectConfiguration={project} transition={transition()} />);

    expect(wrapper.text()).toContain('There was a problem loading the clusters for this project.');
    expect(wrapper.text()).toContain('There was a problem loading the executions for this project.');

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
