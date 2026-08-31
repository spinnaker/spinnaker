import type { Transition } from '@uirouter/core';
import { UISref } from '@uirouter/react';
import React from 'react';

import { getAvailableProjectClusterRegions, ProjectCluster } from './ProjectCluster';
import type { IProjectDashboardCluster, IRegionSelection } from './ProjectClusterModel';
import { getProjectPipelineConfigIds, getProjectPipelineGroups } from './ProjectPipelineModel';
import type { IProjectPipelineGroup } from './ProjectPipelineModel';
import { RegionFilter } from './RegionFilter';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { useDeckRuntimeServices } from '../../bootstrap/DeckRuntimeContext';
import type { IExecution, IPipeline, IProject } from '../../domain';
import { RecentHistoryService } from '../../history/recentHistory.service';
import { ProjectPipeline } from './pipeline/ProjectPipeline';
import { PipelineConfigService } from '../../pipeline/config/services/PipelineConfigService';
import { ProjectReader } from '../service/ProjectReader';
import { Spinner } from '../../widgets';

import './dashboard.less';

export interface IProjectDashboardProps {
  projectConfiguration: IProject;
  transition: Transition;
}

interface ILoadState {
  initializing: boolean;
  refreshing: boolean;
  loaded: boolean;
  error: boolean;
  warning?: boolean;
  lastRefresh?: number;
}

const initialLoadState = (): ILoadState => ({
  initializing: true,
  refreshing: false,
  loaded: false,
  error: false,
});

const getSelectedRegionsFromTransition = (transition: Transition): IRegionSelection => {
  const rawRegions = transition.params().reg;

  if (!rawRegions) {
    return {};
  }

  if (Array.isArray(rawRegions)) {
    return rawRegions.reduce((acc, region) => ({ ...acc, [region]: true }), {} as IRegionSelection);
  }

  if (typeof rawRegions === 'string') {
    return rawRegions
      .split(',')
      .filter(Boolean)
      .reduce((acc, region) => ({ ...acc, [region]: true }), {} as IRegionSelection);
  }

  return rawRegions;
};

const getAllRegions = (clusters: IProjectDashboardCluster[]): string[] =>
  Array.from(
    new Set(
      clusters.reduce(
        (regions, cluster) => [...regions, ...getAvailableProjectClusterRegions(cluster)],
        [] as string[],
      ),
    ),
  ).sort();

const removeUnselectedRegions = (selectedRegions: IRegionSelection): IRegionSelection =>
  Object.keys(selectedRegions).reduce((acc, region) => {
    if (selectedRegions[region]) {
      acc[region] = true;
    }
    return acc;
  }, {} as IRegionSelection);

const RefreshControl = ({ onRefresh, refreshing }: { onRefresh: () => void; refreshing: boolean }) => (
  <button className="btn btn-link btn-xs" onClick={onRefresh} type="button">
    <span className={`glyphicon glyphicon-refresh ${refreshing ? 'fa-spin' : ''}`} />
  </button>
);

export const ProjectDashboard = ({ projectConfiguration: project, transition }: IProjectDashboardProps) => {
  const { executionService } = useDeckRuntimeServices();
  const [clusters, setClusters] = React.useState<IProjectDashboardCluster[]>([]);
  const [pipelineGroups, setPipelineGroups] = React.useState<IProjectPipelineGroup[]>([]);
  const [clusterState, setClusterState] = React.useState<ILoadState>(initialLoadState());
  const [executionState, setExecutionState] = React.useState<ILoadState>(initialLoadState());
  const [selectedRegions, setSelectedRegions] = React.useState<IRegionSelection>(() =>
    getSelectedRegionsFromTransition(transition),
  );
  const [application] = React.useState(() => ApplicationModelBuilder.createStandaloneApplication('project'));
  const clusterLoadGeneration = React.useRef(0);
  const pipelineLoadGeneration = React.useRef(0);

  const applyRegionFilter = (nextSelectedRegions: IRegionSelection) => {
    const selected = removeUnselectedRegions(nextSelectedRegions);
    setSelectedRegions(selected);
    transition.router.stateService.go('.', { reg: selected }, { location: 'replace' });
  };

  const loadClusters = () => {
    const loadGeneration = ++clusterLoadGeneration.current;
    setClusterState((state) => ({ ...state, refreshing: true, error: false }));
    const configuredClusters = project.config?.clusters?.length;
    const clustersPromise: Promise<IProjectDashboardCluster[]> = configuredClusters
      ? ProjectReader.getProjectClusters(project.name)
      : configuredClusters === 0
      ? Promise.resolve([])
      : Promise.reject(null);

    return clustersPromise
      .then((nextClusters: IProjectDashboardCluster[]) => {
        if (loadGeneration !== clusterLoadGeneration.current) {
          return;
        }
        setClusters(nextClusters);
        setClusterState({
          initializing: false,
          refreshing: false,
          loaded: true,
          error: false,
          lastRefresh: Date.now(),
        });
      })
      .catch(() => {
        if (loadGeneration === clusterLoadGeneration.current) {
          setClusterState((state) => ({ ...state, initializing: false, refreshing: false, error: true }));
        }
      });
  };

  const loadManualPipelineFallback = (loadGeneration: number) =>
    executionService.getProjectExecutionsForConfigIds(getProjectPipelineConfigIds(project, [])).then(
      (nextExecutions: IExecution[]) => {
        if (loadGeneration !== pipelineLoadGeneration.current) {
          return;
        }
        setPipelineGroups(getProjectPipelineGroups(project, [], nextExecutions));
        setExecutionState({
          initializing: false,
          refreshing: false,
          loaded: true,
          error: false,
          warning: true,
          lastRefresh: Date.now(),
        });
      },
      () => {
        if (loadGeneration === pipelineLoadGeneration.current) {
          setExecutionState((state) => ({ ...state, initializing: false, refreshing: false, error: true }));
        }
      },
    );

  const loadPipelines = async () => {
    const loadGeneration = ++pipelineLoadGeneration.current;
    setExecutionState((state) => ({ ...state, refreshing: true, error: false, warning: false }));
    setPipelineGroups([]);

    let pipelineConfigs: IPipeline[];
    try {
      pipelineConfigs = await PipelineConfigService.getAllPipelineConfigs();
    } catch {
      return loadManualPipelineFallback(loadGeneration);
    }

    try {
      const pipelineConfigIds = getProjectPipelineConfigIds(project, pipelineConfigs);
      const nextExecutions = await executionService.getProjectExecutionsForConfigIds(pipelineConfigIds);
      if (loadGeneration !== pipelineLoadGeneration.current) {
        return;
      }
      setPipelineGroups(getProjectPipelineGroups(project, pipelineConfigs, nextExecutions));
      setExecutionState({
        initializing: false,
        refreshing: false,
        loaded: true,
        error: false,
        warning: false,
        lastRefresh: Date.now(),
      });
    } catch {
      if (loadGeneration === pipelineLoadGeneration.current) {
        setExecutionState((state) => ({ ...state, initializing: false, refreshing: false, error: true }));
      }
    }
  };

  React.useEffect(() => {
    if (project.notFound) {
      RecentHistoryService.removeLastItem('projects');
      return undefined;
    }

    RecentHistoryService.addExtraDataToLatest('projects', {
      config: {
        applications: project.config.applications,
      },
    });

    loadClusters();
    loadPipelines();

    const refreshInterval = window.setInterval(() => {
      loadClusters();
      loadPipelines();
    }, 3 * 60 * 1000);

    return () => window.clearInterval(refreshInterval);
  }, [project.name]);

  const toggleRegion = (region: string) => {
    applyRegionFilter({ ...selectedRegions, [region]: !selectedRegions[region] });
  };

  const allRegions = getAllRegions(clusters);

  if (project.notFound) {
    return null;
  }

  return (
    <div className="project-dashboard container">
      <div className="row">
        <div className="col-md-7 project-column">
          <h3>
            Application Status
            <RefreshControl onRefresh={loadClusters} refreshing={clusterState.refreshing} />
            <RegionFilter
              onClear={() => applyRegionFilter({})}
              onToggleRegion={toggleRegion}
              regions={allRegions}
              selectedRegions={selectedRegions}
            />
          </h3>
          {clusters.map((cluster, index) => (
            <ProjectCluster
              key={`${cluster.account}:${cluster.stack}:${cluster.detail}:${index}`}
              cluster={cluster}
              project={project}
              selectedRegions={selectedRegions}
            />
          ))}
          {!clusterState.loaded && (
            <div className="horizontal center">
              <Spinner size="small" />
            </div>
          )}
          {!project.config.clusters.length && <h4>No clusters configured</h4>}
          {clusterState.error && <h4>There was a problem loading the clusters for this project.</h4>}
        </div>
        <div className="col-md-5 project-column">
          <h3>
            Pipeline Status
            <RefreshControl onRefresh={loadPipelines} refreshing={executionState.refreshing} />
          </h3>
          {!executionState.loaded && (
            <div className="horizontal center">
              <Spinner size="small" />
            </div>
          )}
          {pipelineGroups.map((group) => (
            <section className="project-pipeline-group" key={group.application}>
              <h4>{group.application}</h4>
              {group.pipelines.map((row) =>
                row.execution ? (
                  <section className="project-pipeline" key={row.pipelineConfigId}>
                    <ProjectPipeline application={application} execution={row.execution} />
                  </section>
                ) : (
                  <div className="project-pipeline-never-run" key={row.pipelineConfigId}>
                    <UISref
                      to="home.applications.application.pipelines.pipelineConfig"
                      params={{ application: row.application, pipelineId: row.pipelineConfigId }}
                    >
                      <a>{row.name}</a>
                    </UISref>
                    <span>Never run</span>
                  </div>
                ),
              )}
            </section>
          ))}
          {executionState.loaded && !pipelineGroups.length && <h4>No pipelines found</h4>}
          {executionState.warning && (
            <div className="alert alert-warning">
              Automatic pipeline discovery is unavailable. Showing manually configured pipeline executions.
            </div>
          )}
          {executionState.error && <h4>There was a problem loading the executions for this project.</h4>}
        </div>
      </div>
    </div>
  );
};
