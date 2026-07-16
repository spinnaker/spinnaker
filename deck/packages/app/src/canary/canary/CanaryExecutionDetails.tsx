import React from 'react';

import type { IExecutionDetailsSectionProps } from '@spinnaker/core';
import { AccountTag, ExecutionDetailsSection, SETTINGS, StageFailureMessage, timestamp } from '@spinnaker/core';

import { CanaryScore } from './CanaryScore';
import { CanaryStatus } from './CanaryStatus';

function join(values: any[]) {
  return values ? values.join(', ') : '';
}

function CanarySummary({ stage }: IExecutionDetailsSectionProps) {
  const canary = stage.context.canary || {};
  const canaryDeployments = canary.canaryDeployments || [];
  return (
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
                  <strong>Deployment</strong>
                </td>
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
                    <strong>{deployment.canaryCluster?.region}</strong>
                  </td>
                  <td>
                    <CanaryScore
                      result={deployment.canaryResult?.result}
                      score={deployment.canaryResult?.score}
                      health={deployment.health?.health}
                    />
                  </td>
                  <td>{deployment.canaryResult?.timeDuration?.durationString || ' - '}</td>
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
            {!canary.canaryResult?.manual &&
              canary.status?.complete &&
              canary.canaryResult?.overallResult === 'failure' &&
              canary.canaryResult?.message && (
                <InfoRow label="Result">
                  <div className="alert canary-summary-alert alert-danger">
                    <strong>{canary.canaryResult.message}</strong>
                  </div>
                </InfoRow>
              )}
          </div>
          <StageFailureMessage stage={stage} message={(stage.exceptions || []).join(', ')} />
        </div>
      </div>
    </div>
  );
}

function CanaryConfig({ stage }: IExecutionDetailsSectionProps) {
  const canary = stage.context.canary || {};
  const canaryConfig = canary.canaryConfig || {};
  const baseline = stage.context.baseline || {};
  const canaryDeployments = canary.canaryDeployments || [];
  const configUrl = SETTINGS.canaryConfig ? SETTINGS.canaryConfig.analysisConfigUrl : null;
  const configName = canaryConfig.canaryAnalysisConfig?.name;
  const configHref = configUrl
    ? `${configUrl}/${canaryDeployments[0]?.baselineCluster?.accountName}/${configName}`
    : null;
  return (
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
          <ConfigRow label="Baseline">
            <AccountTag account={baseline.account} /> {baseline.cluster}
          </ConfigRow>
          <ConfigRow label="Duration">{canaryConfig.lifetimeHours} hours</ConfigRow>
          <ConfigRow label="Success Criteria">{canaryConfig.canarySuccessCriteria?.canaryResultScore}</ConfigRow>
          <ConfigRow label="Result Strategy">
            {(canaryConfig.combinedCanaryResultStrategy || '').toLowerCase()}
          </ConfigRow>
        </div>
        <div className="col-md-12 canary-config-section">
          <h5>Analysis Config</h5>
          <div className="horizontal-rule" />
          <ConfigRow label="Config Name">
            {configHref ? (
              <a href={configHref} target="_blank">
                {configName}
              </a>
            ) : (
              configName
            )}
          </ConfigRow>
          <ConfigRow label="Warmup Period">
            {canaryConfig.canaryAnalysisConfig?.beginCanaryAnalysisAfterMins} minutes
          </ConfigRow>
          <ConfigRow label="Interval">
            {canaryConfig.canaryAnalysisConfig?.canaryAnalysisIntervalMins} minutes
          </ConfigRow>
          <ConfigRow label="Notification Hours">{join(canaryConfig.canaryAnalysisConfig?.notificationHours)}</ConfigRow>
          <ConfigRow label="Canary Report Recipients">{join(canary.recipients)}</ConfigRow>
        </div>
      </div>
      <div className="row">
        <div className="col-md-12 canary-config-section">
          <h5>Health Check</h5>
          <div className="horizontal-rule" />
          <ConfigRow label="Minimum Canary Score">
            {canaryConfig.canaryHealthCheckHandler?.minimumCanaryResultScore}
          </ConfigRow>
          {canaryConfig.actionsForUnhealthyCanary && (
            <ConfigRow label="Unhealthy Canary Actions">
              {canaryConfig.actionsForUnhealthyCanary.map((action: any, index: number) => (
                <span key={index}>
                  <strong>{action.action}</strong>
                  {action.delayBeforeActionInMins <= 0
                    ? ' immediately'
                    : ` after ${action.delayBeforeActionInMins} minutes`}
                  <br />
                </span>
              ))}
            </ConfigRow>
          )}
        </div>
      </div>
    </div>
  );
}

function CanaryExecutionDetailsComponent(props: IExecutionDetailsSectionProps) {
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      {props.name === 'canaryConfig' ? <CanaryConfig {...props} /> : <CanarySummary {...props} />}
    </ExecutionDetailsSection>
  );
}

export const CanaryExecutionDetails = Object.assign(CanaryExecutionDetailsComponent, { title: 'canarySummary' });
export const CanaryExecutionConfigDetails = Object.assign(
  function CanaryExecutionConfigDetailsComponent(props: IExecutionDetailsSectionProps) {
    return <CanaryExecutionDetails {...props} />;
  },
  { title: 'canaryConfig' },
);

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
