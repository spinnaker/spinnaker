import { screen, waitFor, within } from '@testing-library/react';
import type { Transition } from '@uirouter/core';
import React from 'react';
import type { Mock } from 'vitest';

import type { IProjectClusterMetadata, IProjectDashboardCluster } from './ProjectClusterModel';
import { ProjectDashboard } from './ProjectDashboard';
import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';
import type { IExecution, IPipeline, IProject } from '../../domain';
import { RecentHistoryService } from '../../history/recentHistory.service';
import { UrlBuilder } from '../../navigation';
import { PipelineConfigService } from '../../pipeline/config/services/PipelineConfigService';
import { ProjectReader } from '../service/ProjectReader';
import { renderWithRouter, setupUser } from '../../utils/testUtils';

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
} as IProject;

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
} as IProjectDashboardCluster;

const execution = ({
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
} as unknown) as IExecution;

interface TestTransition extends Transition {
  router: Transition['router'] & { stateService: Transition['router']['stateService'] & { go: Mock } };
}

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
} as IPipeline;

const transition = (params: Record<string, unknown> = {}) =>
  (({
    params: () => params,
    router: {
      stateService: {
        go: vi.fn(),
      },
    },
  } as unknown) as TestTransition);

describe('<ProjectDashboard />', () => {
  let executionService: { getProjectExecutions: Mock; getProjectExecutionsForConfigIds: Mock };

  const renderDashboard = (projectConfiguration: IProject, currentTransition = transition()) =>
    renderWithRouter(
      <DeckRuntimeContext.Provider
        value={{ services: { executionService } } as React.ContextType<typeof DeckRuntimeContext>}
      >
        <ProjectDashboard projectConfiguration={projectConfiguration} transition={currentTransition} />
      </DeckRuntimeContext.Provider>,
    );

  const pipelineRefreshButton = () =>
    within(screen.getByRole('heading', { name: 'Pipeline Status' })).getByRole('button');

  const executionsError = 'There was a problem loading the executions for this project.';

  beforeEach(() => {
    vi.spyOn(RecentHistoryService, 'addExtraDataToLatest').mockReturnValue(undefined);
    vi.spyOn(RecentHistoryService, 'removeLastItem').mockReturnValue(undefined);
    vi.spyOn(UrlBuilder, 'buildFromMetadata').mockImplementation((metadata: IProjectClusterMetadata) => {
      const reg = metadata.region ? `?reg=${metadata.region}` : '';
      return `#/projects/${metadata.project}/applications/${metadata.application}/clusters${reg}`;
    });
    vi.spyOn(ProjectReader, 'getProjectClusters').mockResolvedValue([cluster]);
    vi.spyOn(PipelineConfigService, 'getAllPipelineConfigs').mockResolvedValue([
      { ...taggedPipeline },
      { ...taggedPipeline, id: 'deployment', application: 'kubernetesapp', name: 'Deployment', tags: [] },
    ]);
    executionService = {
      getProjectExecutions: vi.fn().mockResolvedValue([execution]),
      getProjectExecutionsForConfigIds: vi.fn().mockResolvedValue([execution]),
    };
  });

  it('loads clusters and executions and renders dashboard columns', async () => {
    const { container } = renderDashboard(project);

    expect(await screen.findByText('KUBERNETESAPP')).toBeInTheDocument();
    expect(await screen.findByText('Never run')).toBeInTheDocument();
    expect(RecentHistoryService.addExtraDataToLatest).toHaveBeenCalledWith('projects', {
      config: { applications: ['kubernetesapp'] },
    });
    expect(ProjectReader.getProjectClusters).toHaveBeenCalledWith('kubernetesproject');
    expect(PipelineConfigService.getAllPipelineConfigs).toHaveBeenCalled();
    expect(executionService.getProjectExecutionsForConfigIds).toHaveBeenCalledWith(['deployment', 'tagged-deployment']);
    expect(container.querySelector('.project-dashboard')).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 3 })[0]).toHaveTextContent('Application Status');
    expect(screen.getByRole('heading', { name: 'Pipeline Status' })).toBeInTheDocument();
    expect(container.querySelectorAll('section.project-cluster')).toHaveLength(1);
    expect(container.querySelectorAll('.project-pipeline-group')).toHaveLength(2);
    expect(container.querySelectorAll('section.project-pipeline')).toHaveLength(1);
    expect(screen.getByText('Storefront deploy')).toBeInTheDocument();
    expect(container.querySelector('project-pipeline')).not.toBeInTheDocument();
  });

  it('skips cluster request and renders empty states when nothing is configured', async () => {
    vi.mocked(ProjectReader.getProjectClusters).mockClear();
    vi.mocked(PipelineConfigService.getAllPipelineConfigs).mockResolvedValue([]);
    const emptyProject = {
      ...project,
      config: { applications: [], clusters: [], pipelineConfigs: [] },
    };

    renderDashboard(emptyProject);

    expect(await screen.findByRole('heading', { name: 'No pipelines found' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'No clusters configured' })).toBeInTheDocument();
    expect(ProjectReader.getProjectClusters).not.toHaveBeenCalled();
  });

  it('renders independent cluster and execution load errors', async () => {
    vi.mocked(ProjectReader.getProjectClusters).mockRejectedValue(new Error('clusters failed'));
    executionService.getProjectExecutionsForConfigIds.mockRejectedValue(new Error('executions failed'));

    renderDashboard(project);

    expect(
      await screen.findByRole('heading', { name: 'There was a problem loading the clusters for this project.' }),
    ).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: executionsError })).toBeInTheDocument();
  });

  it('loads manual pipeline executions through the application-filtered endpoint when config discovery fails', async () => {
    vi.mocked(PipelineConfigService.getAllPipelineConfigs).mockRejectedValue(new Error('configs failed'));

    const { container } = renderDashboard(project);

    expect(await screen.findByText(/Automatic pipeline discovery is unavailable/)).toBeInTheDocument();
    expect(executionService.getProjectExecutionsForConfigIds).toHaveBeenCalledWith(['deployment']);
    expect(executionService.getProjectExecutions).not.toHaveBeenCalled();
    expect(container.querySelectorAll('section.project-pipeline')).toHaveLength(1);
  });

  it('shows an execution error instead of never-run rows when selected execution loading fails', async () => {
    executionService.getProjectExecutionsForConfigIds.mockRejectedValue(new Error('executions failed'));

    renderDashboard(project);

    expect(await screen.findByRole('heading', { name: executionsError })).toBeInTheDocument();
    expect(screen.queryByText('Never run')).not.toBeInTheDocument();
    expect(executionService.getProjectExecutions).not.toHaveBeenCalled();
  });

  it('clears prior never-run rows when a refresh fails', async () => {
    const user = setupUser();
    executionService.getProjectExecutionsForConfigIds.mockResolvedValue([]);

    renderDashboard(project);
    expect(await screen.findAllByText('Never run')).not.toHaveLength(0);

    executionService.getProjectExecutionsForConfigIds.mockRejectedValue(new Error('refresh failed'));
    await user.click(pipelineRefreshButton());

    expect(await screen.findByRole('heading', { name: executionsError })).toBeInTheDocument();
    expect(screen.queryByText('Never run')).not.toBeInTheDocument();
    expect(screen.queryByText('No pipelines found')).not.toBeInTheDocument();
  });

  it('updates execution layout when a refresh returns a new execution', async () => {
    const user = setupUser();
    const refreshedExecution = {
      ...execution,
      id: '02',
      stageSummaries: [
        ...execution.stageSummaries,
        { ...execution.stageSummaries[0], id: '2', refId: '2', index: 1, name: 'Verify' },
      ],
    };
    executionService.getProjectExecutionsForConfigIds
      .mockResolvedValueOnce([execution])
      .mockResolvedValueOnce([refreshedExecution]);

    const { container } = renderDashboard(project);
    const markers = () => Array.from(container.querySelectorAll<HTMLElement>('.execution-marker'));

    await waitFor(() => expect(markers()).toHaveLength(1));
    expect(markers()[0].style.width).toBe('100%');

    await user.click(pipelineRefreshButton());

    await waitFor(() => expect(markers()).toHaveLength(2));
    expect(markers()[0].style.width).toBe('50%');
  });

  it('ignores a superseded pipeline load', async () => {
    const user = setupUser();
    let resolveInitialDiscovery: (configs: IPipeline[]) => void;
    const initialDiscovery = new Promise<IPipeline[]>((resolve) => {
      resolveInitialDiscovery = resolve;
    });
    vi.mocked(PipelineConfigService.getAllPipelineConfigs).mockReturnValue(initialDiscovery);

    renderDashboard(project);
    vi.mocked(PipelineConfigService.getAllPipelineConfigs).mockRejectedValue(new Error('refresh failed'));
    executionService.getProjectExecutionsForConfigIds.mockRejectedValue(new Error('fallback failed'));

    await user.click(pipelineRefreshButton());
    expect(await screen.findByRole('heading', { name: executionsError })).toBeInTheDocument();

    executionService.getProjectExecutionsForConfigIds.mockResolvedValue([]);
    resolveInitialDiscovery([taggedPipeline]);
    await waitFor(() => expect(executionService.getProjectExecutionsForConfigIds).toHaveBeenCalledTimes(2));
    await Promise.resolve();

    expect(screen.getByRole('heading', { name: executionsError })).toBeInTheDocument();
    expect(screen.queryByText('Never run')).not.toBeInTheDocument();
  });

  it('renders an empty state when no manual or tagged pipelines exist', async () => {
    vi.mocked(PipelineConfigService.getAllPipelineConfigs).mockResolvedValue([]);
    const emptyProject = { ...project, config: { applications: [], clusters: [], pipelineConfigs: [] } };

    renderDashboard(emptyProject);

    expect(await screen.findByRole('heading', { name: 'No pipelines found' })).toBeInTheDocument();
    expect(executionService.getProjectExecutionsForConfigIds).toHaveBeenCalledWith([]);
  });

  it('toggles region filters and replaces the current route params', async () => {
    const user = setupUser();
    const currentTransition = transition({ reg: { dev: true } });
    renderDashboard(project, currentTransition);
    await screen.findByText('KUBERNETESAPP');

    await user.click(screen.getByText('Filter by region / namespace'));
    await user.click(screen.getByText('dev', { selector: 'label' }));

    expect(currentTransition.router.stateService.go).toHaveBeenCalledWith('.', { reg: {} }, { location: 'replace' });
  });

  it('renders nothing for missing projects and removes recent history', () => {
    const { container } = renderDashboard({ ...project, notFound: true });

    expect(RecentHistoryService.removeLastItem).toHaveBeenCalledWith('projects');
    expect(container).toBeEmptyDOMElement();
  });
});
