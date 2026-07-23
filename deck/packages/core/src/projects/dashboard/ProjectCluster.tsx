import React from 'react';

import type { IProjectClusterMetadata, IProjectDashboardCluster, IRegionSelection } from './ProjectClusterModel';
import { getProjectClusterRegions, getProjectClusterViewModel } from './ProjectClusterModel';
import { CollapsibleSectionStateCache } from '../../cache';
import type { IProject } from '../../domain';
import { HealthCounts } from '../../healthCounts/HealthCounts';
import { ClusterState } from '../../state';
import { relativeTime } from '../../utils/timeFormatters';

import './cluster/projectCluster.less';

export interface IProjectClusterProps {
  cluster: IProjectDashboardCluster;
  project: IProject;
  selectedRegions: IRegionSelection;
}

const getCacheKey = (project: IProject, cluster: IProjectDashboardCluster): string =>
  [project.name, cluster.account, cluster.stack].join(':');

const clearFilters = (metadata: IProjectClusterMetadata) => {
  ClusterState.filterService.overrideFiltersForUrl(metadata);
};

const renderBuild = (application: ReturnType<typeof getProjectClusterViewModel>['applications'][number]) => {
  if (!application.build) {
    return null;
  }

  if (application.build.images) {
    return (
      <ul className="list-unstyled">
        {application.build.images.map((image) => (
          <li key={image}>{image}</li>
        ))}
      </ul>
    );
  }

  if (application.build.buildNumber) {
    return (
      <a className="heavy" href={application.build.url} target="_blank">
        <span>#</span>
        {application.build.buildNumber}
      </a>
    );
  }

  return null;
};

export const ProjectCluster = ({ project, cluster, selectedRegions }: IProjectClusterProps) => {
  const cacheKey = getCacheKey(project, cluster);
  const [expanded, setExpanded] = React.useState(
    CollapsibleSectionStateCache.isSet(cacheKey) ? CollapsibleSectionStateCache.isExpanded(cacheKey) : true,
  );
  const viewModel = getProjectClusterViewModel(project, cluster, selectedRegions);

  const toggle = () => {
    CollapsibleSectionStateCache.setExpanded(cacheKey, !expanded);
    setExpanded(!expanded);
  };

  return (
    <project-cluster>
      <div className="row rollup-entry sub-group">
        <div className="rollup-summary">
          <div className="container-fluid no-padding">
            <div className="row clickable" onClick={toggle}>
              <div className="col-md-12">
                <div className="rollup-title-cell">
                  <span className="account-tag account-tag-wrapper">
                    <span className="account-tag-name">{cluster.account}</span>
                  </span>
                  <div className="pod-center horizontal space-between center flex-1">
                    <span className="cluster-name">{viewModel.clusterLabel}</span>
                  </div>
                  <span className="cluster-health">
                    {cluster.applications.length} Application{cluster.applications.length === 1 ? '' : 's'}
                  </span>
                  <span className="cluster-health"> {viewModel.instanceCounts.total} Instances </span>
                  <HealthCounts container={viewModel.instanceCounts} />
                </div>
              </div>
            </div>
          </div>
        </div>
        {expanded && (
          <div className="rollup-details">
            {!cluster.applications.length && (
              <div className="text-center">
                <p>No clusters found for any applications.</p>
              </div>
            )}
            {!!cluster.applications.length && !viewModel.regions.length && (
              <div className="text-center">
                <p>No clusters found for selected regions / namespaces.</p>
              </div>
            )}
            {!!cluster.applications.length && !!viewModel.regions.length && (
              <table className="table table-condensed">
                <thead>
                  <tr>
                    <th style={{ width: '18%' }} />
                    <th style={{ width: '9%' }} />
                    <th style={{ width: '15%' }}>Last Push</th>
                    {viewModel.regions.map((region) => (
                      <th key={region} style={{ width: `${57 / viewModel.regions.length}%` }}>
                        {region}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {viewModel.applications
                    .slice()
                    .sort((a, b) => a.application.localeCompare(b.application))
                    .map((application) => (
                      <tr key={application.application}>
                        <td>
                          <a
                            className={`heavy ${application.hasInconsistentBuilds ? 'text-warning' : ''}`}
                            href={application.metadata.href}
                            onClick={() => clearFilters(application.metadata)}
                          >
                            {application.application.toUpperCase()}
                          </a>
                        </td>
                        <td>
                          {renderBuild(application)}
                          {application.hasInconsistentBuilds && (
                            <i className="fa fa-exclamation-triangle text-warning" />
                          )}
                        </td>
                        <td>
                          {application.lastPush ? (
                            <span className="small"> {relativeTime(application.lastPush)} </span>
                          ) : (
                            <span> - </span>
                          )}
                        </td>
                        {viewModel.regions.map((region) => {
                          const regionCluster = application.regions[region];
                          return (
                            <td key={region}>
                              {regionCluster ? (
                                <span>
                                  <a
                                    href={regionCluster.metadata.href}
                                    onClick={() => clearFilters(regionCluster.metadata)}
                                  >
                                    <HealthCounts
                                      container={regionCluster.instanceCounts}
                                      additionalLegendText="(Click to view cluster in this region)"
                                      legendPlacement="right"
                                    />
                                  </a>
                                  {!!regionCluster.inconsistentBuilds?.length && (
                                    <i className="fa fa-exclamation-triangle" />
                                  )}
                                </span>
                              ) : (
                                <span> - </span>
                              )}
                            </td>
                          );
                        })}
                      </tr>
                    ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </div>
    </project-cluster>
  );
};

export const getAvailableProjectClusterRegions = getProjectClusterRegions;
