import React from 'react';

import type { IExecutionDetailsSectionProps } from '@spinnaker/core';
import { AccountTag, ExecutionDetailsSection, SETTINGS, StageFailureMessage, timestamp } from '@spinnaker/core';

import { CanaryScore } from '../canary/CanaryScore';
import { CanaryStatus } from '../canary/CanaryStatus';
import { getCanaryAnalysisHistory } from '../canary/canaryDeployment/canaryDeploymentHistory';

function History({ stage }: IExecutionDetailsSectionProps) {
  const deployment = stage.context;
  const [history, setHistory] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState(false);
  const loadHistory = () => {
    if (deployment.canary?.canaryDeployments?.length > 0) {
      setLoading(true);
      setError(false);
      getCanaryAnalysisHistory(deployment.canary.canaryDeployments[0].id).then(
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

function AcaTaskExecutionDetailsComponent(props: IExecutionDetailsSectionProps) {
  const canary = props.stage.context.canary || {};
  const canaryConfig = canary.canaryConfig || {};
  const canaryDeployments = canary.canaryDeployments || [];
  const queryListUrl = SETTINGS.canaryConfig ? SETTINGS.canaryConfig.queryListUrl : null;
  const configHref =
    queryListUrl && canaryConfig.canaryAnalysisConfig?.name
      ? `${queryListUrl}/${canaryConfig.canaryAnalysisConfig.name}/edit`
      : null;
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      {props.name === 'canaryAnalysisHistory' ? (
        <History {...props} />
      ) : props.name === 'canaryConfig' ? (
        <div className="canary-details">
          <div className="row">
            <div className="col-md-12">
              <div className="row">
                <div className="col-md-4">
                  <h5>Name</h5>
                </div>
                <div className="col-md-6">
                  <h5>{canaryConfig.name}</h5>
                </div>
              </div>
              <div className="horizontal-rule" />
            </div>
          </div>
          <div className="row">
            <div className="col-md-12 canary-config-section">
              {canaryDeployments.length > 0 && (
                <ConfigRow label="Account">
                  <AccountTag account={canaryDeployments[0].accountName} />
                </ConfigRow>
              )}
              <ConfigRow label="Duration">{canaryConfig.lifetimeHours} hours</ConfigRow>
              <ConfigRow label="Success Criteria">{canaryConfig.canarySuccessCriteria?.canaryResultScore}</ConfigRow>
              <ConfigRow label="Result Strategy">
                {(canaryConfig.combinedCanaryResultStrategy || '').toLowerCase()}
              </ConfigRow>
              {canaryDeployments.length > 0 && <ConfigRow label="Scope Type">{canaryDeployments[0].type}</ConfigRow>}
              {canaryDeployments.length > 0 && (
                <ConfigRow label="Baseline">
                  <span style={{ wordWrap: 'break-word' }}>{canaryDeployments[0].baseline}</span>
                </ConfigRow>
              )}
              {canaryDeployments.length > 0 && (
                <ConfigRow label="Canary">
                  <span style={{ wordWrap: 'break-word' }}>{canaryDeployments[0].canary}</span>
                </ConfigRow>
              )}
            </div>
            <div className="col-md-12 canary-config-section">
              <h5>Analysis Config</h5>
              <div className="horizontal-rule" />
              <ConfigRow label="Config Name">
                {configHref ? (
                  <a href={configHref} target="_blank">
                    {canaryConfig.canaryAnalysisConfig?.name}
                  </a>
                ) : (
                  canaryConfig.canaryAnalysisConfig?.name
                )}
              </ConfigRow>
              <ConfigRow label="Warmup Period">
                {canaryConfig.canaryAnalysisConfig?.beginCanaryAnalysisAfterMins} minutes
              </ConfigRow>
              <ConfigRow label="Interval">
                {canaryConfig.canaryAnalysisConfig?.canaryAnalysisIntervalMins} minutes
              </ConfigRow>
              <ConfigRow label="Notification Hours">
                {(canaryConfig.canaryAnalysisConfig?.notificationHours || []).join(', ')}
              </ConfigRow>
              <ConfigRow label="Canary Report Recipients">{(canary.recipients || []).join(', ')}</ConfigRow>
            </div>
          </div>
          <div className="row">
            <div className="col-md-12 canary-config-section">
              <h5>Health Check</h5>
              <div className="horizontal-rule" />
              <ConfigRow label="Minimum Canary Score">
                {canaryConfig.canaryHealthCheckHandler?.minimumCanaryResultScore}
              </ConfigRow>
            </div>
          </div>
        </div>
      ) : (
        <div className="canary-details">
          <div className="row">
            <div className="col-md-2 canary-summary">
              <div className="score score-large">
                <CanaryScore
                  health={canary.health?.health}
                  result={canary.canaryResult?.overallResult}
                  score={canary.canaryResult?.overallScore}
                />
              </div>
            </div>
          </div>
          <div className="row">
            <div className="col-md-12">
              <table className="table">
                <tbody>
                  <tr>
                    <td style={{ borderTop: 'none' }}>
                      <strong>Canary Result</strong>
                    </td>
                    <td style={{ borderTop: 'none' }}>
                      <strong>Duration</strong>
                    </td>
                    <td style={{ borderTop: 'none' }}>
                      <strong>Report</strong>
                    </td>
                    <td style={{ borderTop: 'none' }}>
                      <strong>Last Updated</strong>
                    </td>
                  </tr>
                  {canaryDeployments.map((deployment: any, index: number) => (
                    <tr key={index}>
                      <td>
                        <CanaryScore
                          result={deployment.canaryAnalysisResult?.result}
                          score={deployment.canaryAnalysisResult?.score}
                          health={deployment.health?.health}
                        />
                      </td>
                      <td>{deployment.canaryAnalysisResult?.timeDuration?.durationString || ' - '}</td>
                      <td>
                        {deployment.canaryAnalysisResult?.canaryReportURL && (
                          <a target="_blank" href={deployment.canaryAnalysisResult.canaryReportURL}>
                            Canary Report
                          </a>
                        )}
                      </td>
                      <td>
                        {deployment.canaryAnalysisResult?.lastUpdated
                          ? timestamp(deployment.canaryAnalysisResult.lastUpdated)
                          : ' - '}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <div className="row">
            <div className="col-md-12">
              <div className="well alert alert-info">
                {canary.launchedDate && <InfoRow label="Launched">{timestamp(canary.launchedDate)}</InfoRow>}
                {canary.endDate && <InfoRow label="Ended">{timestamp(canary.endDate)}</InfoRow>}
                <InfoRow label="Status">
                  <h5>
                    <CanaryStatus status={canary.status?.status} />
                  </h5>
                </InfoRow>
                {canary.status?.reason && <InfoRow label="Message">{canary.status.reason}</InfoRow>}
                {canary.canaryResult?.manual && (
                  <InfoRow label="Result">
                    <div className="alert canary-summary-alert alert-danger">
                      <strong>Canary result has been manually set</strong>
                    </div>
                  </InfoRow>
                )}
              </div>
              <StageFailureMessage stage={props.stage} message={(props.stage.exceptions || []).join(', ')} />
            </div>
          </div>
        </div>
      )}
    </ExecutionDetailsSection>
  );
}

export const AcaTaskExecutionDetails = Object.assign(AcaTaskExecutionDetailsComponent, { title: 'canarySummary' });
export const AcaTaskConfigDetails = Object.assign(
  function AcaTaskConfigDetailsComponent(props: IExecutionDetailsSectionProps) {
    return <AcaTaskExecutionDetails {...props} />;
  },
  { title: 'canaryConfig' },
);
export const AcaTaskHistoryDetails = Object.assign(
  function AcaTaskHistoryDetailsComponent(props: IExecutionDetailsSectionProps) {
    return <AcaTaskExecutionDetails {...props} />;
  },
  { title: 'canaryAnalysisHistory' },
);

export function HistoryTable({
  history,
  loading,
  error,
  loadHistory,
}: {
  history: any[];
  loading: boolean;
  error: boolean;
  loadHistory: () => void;
}) {
  const sorted = [...(history || [])].sort((a, b) => (b.lastUpdated || 0) - (a.lastUpdated || 0));
  return (
    <div className="row">
      <div className="col-md-12">
        {loading && <p className="text-center">Loading...</p>}
        {!loading && error && (
          <p className="text-center">
            Canary analysis history could not be loaded. <br />
            <a onClick={loadHistory}>Reload score history</a>
          </p>
        )}
        {!loading && !error && (
          <table className="table">
            <thead>
              <tr>
                <th>Score</th>
                <th>Score Duration</th>
                <th>Generated On</th>
                <th>Report</th>
              </tr>
            </thead>
            <tbody>
              {sorted.map((scoreReport: any, index: number) => (
                <tr key={index}>
                  <td>
                    <CanaryScore result={scoreReport.result} score={scoreReport.score} health={scoreReport.health} />
                  </td>
                  <td>{scoreReport.timeDuration?.durationString}</td>
                  <td>{scoreReport.lastUpdated ? timestamp(scoreReport.lastUpdated) : null}</td>
                  <td>
                    {scoreReport.canaryReportURL && (
                      <a target="_blank" href={scoreReport.canaryReportURL}>
                        Canary Report
                      </a>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {!loading && !history.length && <p className="text-center">No canary analysis history available</p>}
      </div>
    </div>
  );
}

function InfoRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="row" style={{ marginTop: 6 }}>
      <div className="col-md-2">
        <strong>{label}</strong>
      </div>
      <div className="col-md-9">{children}</div>
    </div>
  );
}
function ConfigRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="row">
      <div className="col-md-4 sm-label-right compact">{label}</div>
      <div className="col-md-6">{children}</div>
    </div>
  );
}
