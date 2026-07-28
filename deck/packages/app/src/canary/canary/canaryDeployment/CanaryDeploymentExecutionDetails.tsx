import React from 'react';

import type { IExecutionDetailsSectionProps, IRouterInjectedProps } from '@spinnaker/core';
import { ClusterState, ExecutionDetailsSection, timestamp, UrlBuilder, withRouter } from '@spinnaker/core';

import { CanaryScore } from '../CanaryScore';
import { HistoryTable } from '../../acaTask/AcaTaskExecutionDetails';
import { getCanaryAnalysisHistory } from './canaryDeploymentHistory';

function buildClusterUrl(stage: any, cluster: any, project: string) {
  const metadata: any = {
    type: 'clusters',
    application: stage.context.application,
    cluster: cluster.name,
    account: cluster.accountName,
    project,
  };
  metadata.href = UrlBuilder.buildFromMetadata(metadata);
  return metadata;
}

function AnalysisHistory({ stage }: IExecutionDetailsSectionProps) {
  const deployment = stage.context;
  const [history, setHistory] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState(false);
  const loadHistory = () => {
    if (deployment.canaryDeploymentId) {
      setLoading(true);
      setError(false);
      getCanaryAnalysisHistory(deployment.canaryDeploymentId).then(
        (results: any[]) => {
          setHistory(results);
          setLoading(false);
        },
        () => {
          setLoading(false);
          setError(true);
        },
      );
    } else {
      setHistory([]);
      setLoading(false);
    }
  };
  React.useEffect(loadHistory, [deployment]);
  return <HistoryTable history={history} loading={loading} error={error} loadHistory={loadHistory} />;
}

function CodeChanges({ stage }: IExecutionDetailsSectionProps) {
  const commits = stage.context.commits || [];
  return (
    <table>
      <tbody>
        <tr>
          <th>#</th>
          <th>Date</th>
          <th>Commit</th>
          <th>Message</th>
          <th>Author</th>
        </tr>
        {commits.map((commit: any, index: number) => (
          <tr key={index}>
            <td>
              <b>{index + 1}</b>&nbsp;&nbsp;
            </td>
            <td>
              {commit.timestamp
                ? new Date(commit.timestamp).toLocaleDateString(undefined, { month: '2-digit', day: '2-digit' })
                : null}
              &nbsp;&nbsp;
            </td>
            <td>
              <a target="_blank" href={commit.commitUrl}>
                {commit.displayId}
              </a>
              &nbsp;&nbsp;
            </td>
            <td>{(commit.message || '').slice(0, 50)}&nbsp;&nbsp;</td>
            <td>{commit.authorDisplayName}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function DeploymentDetails({ stage, project }: IExecutionDetailsSectionProps & { project: string }) {
  const deployment = stage.context;
  const baselineClusterUrl = deployment.baselineCluster
    ? buildClusterUrl(stage, deployment.baselineCluster, project)
    : null;
  const canaryClusterUrl = deployment.canaryCluster ? buildClusterUrl(stage, deployment.canaryCluster, project) : null;
  return (
    <div className="canary-details">
      <div className="row">
        <div className="col-md-12 canary-summary">
          <div className="score score-large">
            <CanaryScore
              health={deployment.status?.health}
              result={deployment.status?.result}
              score={deployment.status?.score}
            />
          </div>
          <div className="canary-summary-section">
            {deployment.canaryResult?.lastUpdated ? (
              <span>
                <strong>Last updated&nbsp;:&nbsp;&nbsp;</strong>
                {timestamp(deployment.canaryResult.lastUpdated)}
              </span>
            ) : (
              <span>No canary result available</span>
            )}
            <br />
            {deployment.canaryResult?.lastUpdated && (
              <span>
                <strong>Score duration&nbsp;:&nbsp;&nbsp;</strong>
                {deployment.canaryResult.timeDuration?.durationString}
              </span>
            )}
          </div>
          <div className="canary-summary-section">
            {deployment.status?.reportUrl && (
              <a target="_blank" href={deployment.status.reportUrl}>
                Canary Report
              </a>
            )}
            <br />
            <span style={{ visibility: 'hidden' }}>Diff Report</span>
          </div>
        </div>
      </div>
      <div className="row divider" style={{ marginTop: 10 }} />
      <div className="row">
        <ClusterColumn title="Baseline" cluster={deployment.baselineCluster} clusterUrl={baselineClusterUrl} />
        <ClusterColumn title="Canary" cluster={deployment.canaryCluster} clusterUrl={canaryClusterUrl} />
      </div>
    </div>
  );
}

function ClusterColumn({ title, cluster, clusterUrl }: { title: string; cluster: any; clusterUrl: any }) {
  return (
    <div className="col-md-6">
      <strong>{title}</strong>
      <div className="horizontal-rule" />
      <div className="row">
        <div className="col-md-3">
          <strong>Cluster</strong>
        </div>
        <div className="col-md-9">
          {clusterUrl ? (
            <a onClick={() => ClusterState.filterService.overrideFiltersForUrl(clusterUrl)} href={clusterUrl.href}>
              {cluster?.name}
            </a>
          ) : (
            cluster?.name
          )}
        </div>
      </div>
      <div className="row">
        <div className="col-md-3">
          <strong>Image</strong>
        </div>
        <div className="col-md-9">{cluster?.imageId}</div>
      </div>
      <div className="row">
        <div className="col-md-3">
          <strong>Build</strong>
        </div>
        <div className="col-md-9">
          {cluster?.build?.url ? (
            <a href={cluster.build.url} target="_blank">
              #{cluster.build.number}
            </a>
          ) : (
            'n/a'
          )}
        </div>
      </div>
      <div className="row">
        <div className="col-md-3">
          <strong>Capacity</strong>
        </div>
        <div className="col-md-9">{cluster?.capacity}</div>
      </div>
    </div>
  );
}

export function CanaryDeploymentExecutionDetailsComponent(props: IExecutionDetailsSectionProps & IRouterInjectedProps) {
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      {props.name === 'canaryAnalysisHistory' ? (
        <AnalysisHistory {...props} />
      ) : props.name === 'codeChanges' ? (
        <CodeChanges {...props} />
      ) : (
        <DeploymentDetails {...props} project={props.stateParams.project} />
      )}
    </ExecutionDetailsSection>
  );
}

export const CanaryDeploymentExecutionDetails = Object.assign(withRouter(CanaryDeploymentExecutionDetailsComponent), {
  title: 'canaryDeployment',
});
export const CanaryDeploymentAnalysisHistory = Object.assign(
  function CanaryDeploymentAnalysisHistoryComponent(props: IExecutionDetailsSectionProps) {
    return <CanaryDeploymentExecutionDetails {...props} />;
  },
  { title: 'canaryAnalysisHistory' },
);
export const CanaryDeploymentCodeChanges = Object.assign(
  function CanaryDeploymentCodeChangesComponent(props: IExecutionDetailsSectionProps) {
    return <CanaryDeploymentExecutionDetails {...props} />;
  },
  { title: 'codeChanges' },
);
