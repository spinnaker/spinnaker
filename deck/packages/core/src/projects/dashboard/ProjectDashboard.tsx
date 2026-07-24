import type { Transition } from '@uirouter/core';
import React from 'react';

import { getAvailableProjectClusterRegions, ProjectCluster } from './ProjectCluster';
import type { IProjectDashboardCluster, IRegionSelection } from './ProjectClusterModel';
import { RegionFilter } from './RegionFilter';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { useDeckRuntimeServices } from '../../bootstrap/DeckRuntimeContext';
import type { IExecution, IProject } from '../../domain';
import { RecentHistoryService } from '../../history/recentHistory.service';
import { ProjectPipeline } from './pipeline/ProjectPipeline';
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
  const [executions, setExecutions] = React.useState<IExecution[]>([]);
  const [clusterState, setClusterState] = React.useState<ILoadState>(initialLoadState());
  const [executionState, setExecutionState] = React.useState<ILoadState>(initialLoadState());
  const [selectedRegions, setSelectedRegions] = React.useState<IRegionSelection>(() =>
    getSelectedRegionsFromTransition(transition),
  );
  const [application] = React.useState(() => ApplicationModelBuilder.createStandaloneApplication('project'));

  const applyRegionFilter = (nextSelectedRegions: IRegionSelection) => {
    const selected = removeUnselectedRegions(nextSelectedRegions);
    setSelectedRegions(selected);
    transition.router.stateService.go('.', { reg: selected }, { location: 'replace' });
  };

  const loadClusters = () => {
    setClusterState((state) => ({ ...state, refreshing: true, error: false }));
    const configuredClusters = project.config?.clusters?.length;
    const clustersPromise: PromiseLike<IProjectDashboardCluster[]> = configuredClusters
      ? ((ProjectReader.getProjectClusters(project.name) as unknown) as PromiseLike<IProjectDashboardCluster[]>)
      : configuredClusters === 0
      ? Promise.resolve([])
      : Promise.reject(null);

    return clustersPromise
      .then((nextClusters: IProjectDashboardCluster[]) => {
        setClusters(nextClusters);
        setClusterState({
          initializing: false,
          refreshing: false,
          loaded: true,
          error: false,
          lastRefresh: Date.now(),
        });
      })
      .catch(() => setClusterState((state) => ({ ...state, initializing: false, refreshing: false, error: true })));
  };

  const loadExecutions = () => {
    setExecutionState((state) => ({ ...state, refreshing: true, error: false }));
    return executionService
      .getProjectExecutions(project.name)
      .then((nextExecutions: IExecution[]) => {
        setExecutions(nextExecutions);
        setExecutionState({
          initializing: false,
          refreshing: false,
          loaded: true,
          error: false,
          lastRefresh: Date.now(),
        });
      })
      .catch(() => setExecutionState((state) => ({ ...state, initializing: false, refreshing: false, error: true })));
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
    loadExecutions();

    const refreshInterval = window.setInterval(() => {
      loadClusters();
      loadExecutions();
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
            <RefreshControl onRefresh={loadExecutions} refreshing={executionState.refreshing} />
          </h3>
          {!executionState.loaded && (
            <div className="horizontal center">
              <Spinner size="small" />
            </div>
          )}
          {executions.map((execution) => (
            <project-pipeline key={execution.id}>
              <ProjectPipeline application={application} execution={execution} />
            </project-pipeline>
          ))}
          {!project.config.pipelineConfigs.length && <h4>No pipelines configured</h4>}
          {executionState.error && <h4>There was a problem loading the executions for this project.</h4>}
        </div>
      </div>
    </div>
  );
};
