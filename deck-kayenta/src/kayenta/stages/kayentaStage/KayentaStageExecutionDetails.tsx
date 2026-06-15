import { CanaryScore } from 'kayenta/components/canaryScore';
import { KayentaAnalysisType } from 'kayenta/domain';
import { get } from 'lodash';
import React from 'react';

import type { IExecutionDetailsSectionProps, IExecutionStage } from '@spinnaker/core';
import { ExecutionDetailsSection, ReactInjector, robotToHuman, StageFailureMessage, timestamp } from '@spinnaker/core';

import CanaryRunSummaries from './canaryRunSummaries';
import { KAYENTA_CANARY, RUN_CANARY } from './stageTypes';

import './kayentaStageExecutionDetails.less';

const wordWrap = { wordWrap: 'break-word' } as React.CSSProperties;

export function KayentaStageExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { current, name, stage } = props;
  const canaryConfig = stage.context.canaryConfig || {};
  const firstScope = get(canaryConfig, 'scopes[0]', {});
  const firstScopeName = firstScope.scopeName;
  const runCanaryTasks = ((stage.tasks || []) as IExecutionStage[]).filter((task) => task.type === RUN_CANARY);

  return (
    <div className="canary-details">
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-2 canary-summary">
            <div className="score score-large">
              <CanaryScore
                score={stage.context.overallScore}
                health={stage.context.overallHealth}
                result={stage.context.overallResult}
                inverse={false}
              />
            </div>
          </div>
        </div>

        <div className="row">
          <div className="col-md-12 horizontal">
            <CanaryRunSummaries canaryRuns={runCanaryTasks} firstScopeName={firstScopeName} />
          </div>
        </div>

        <StageFailureMessage stage={stage} messages={(stage as any).exceptions || []} />
      </ExecutionDetailsSection>
    </div>
  );
}

export function KayentaStageExecutionConfigDetails(props: IExecutionDetailsSectionProps) {
  const { application, current, name, stage } = props;
  const canaryConfig = stage.context.canaryConfig || {};
  const firstScope = get(canaryConfig, 'scopes[0]', {});
  const firstScopeName = firstScope.scopeName;
  const runCanaryTasks = ((stage.tasks || []) as IExecutionStage[]).filter((task) => task.type === RUN_CANARY);
  const canaryConfigName = resolveCanaryConfigName(application, stage);
  const { resolvedControl, resolvedExperiment } = resolveControlAndExperimentNames(
    stage,
    runCanaryTasks,
    firstScopeName,
  );

  return (
    <div className="canary-details">
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-12 canary-config-section">
            <h5>Scope</h5>
            <div className="horizontal-rule" />
            <DetailRow label="Baseline" value={resolvedControl} />
            <DetailRow label="Canary" value={resolvedExperiment} />
            {firstScope.step && <DetailRow label="Step" value={firstScope.step} />}
            {firstScope.startTimeIso && (
              <DetailRow label="Start Time" value={timestamp(Date.parse(firstScope.startTimeIso))} />
            )}
            {firstScope.endTimeIso && (
              <DetailRow label="End Time" value={timestamp(Date.parse(firstScope.endTimeIso))} />
            )}
          </div>
          <div className="col-md-12 canary-config-section">
            <h5>Analysis Config</h5>
            <div className="horizontal-rule" />
            <div className="row">
              <div className="col-md-4 sm-label-right compact">Config Name</div>
              <div className="col-md-8" style={wordWrap}>
                <a href={canaryConfigHref(canaryConfig.canaryConfigId)}>{canaryConfigName}</a>
              </div>
            </div>
            <DetailRow label="Analysis Type" value={robotToHuman(stage.context.analysisType)} />
            {canaryConfig.lifetimeHours && (
              <DetailRow label="Duration" value={`${canaryConfig.lifetimeHours} hours`} valueClassName="col-md-6" />
            )}
            <DetailRow label="Pass" value={get(canaryConfig, 'scoreThresholds.pass')} valueClassName="col-md-6" />
            <DetailRow
              label="Marginal"
              value={get(canaryConfig, 'scoreThresholds.marginal')}
              valueClassName="col-md-6"
            />
            {canaryConfig.beginCanaryAnalysisAfterMins && (
              <DetailRow
                label="Warmup Period"
                value={`${canaryConfig.beginCanaryAnalysisAfterMins} minutes`}
                valueClassName="col-md-6"
              />
            )}
            {canaryConfig.canaryAnalysisIntervalMins && (
              <DetailRow
                label="Interval"
                value={`${canaryConfig.canaryAnalysisIntervalMins} minutes`}
                valueClassName="col-md-6"
              />
            )}
            {canaryConfig.baselineAnalysisOffsetInMins && (
              <DetailRow
                label="Baseline Offset"
                value={`${canaryConfig.baselineAnalysisOffsetInMins} minutes`}
                valueClassName="col-md-6"
              />
            )}
          </div>
        </div>
      </ExecutionDetailsSection>
    </div>
  );
}

function DetailRow({
  label,
  value,
  valueClassName = 'col-md-8',
}: {
  label: string;
  value: React.ReactNode;
  valueClassName?: string;
}) {
  return (
    <div className="row">
      <div className="col-md-4 sm-label-right compact">{label}</div>
      <div className={valueClassName} style={wordWrap}>
        {value}
      </div>
    </div>
  );
}

function resolveCanaryConfigName(application: IExecutionDetailsSectionProps['application'], stage: IExecutionStage) {
  if (stage.type !== KAYENTA_CANARY) {
    return undefined;
  }

  const canaryConfigId = get(stage, 'context.canaryConfig.canaryConfigId');
  const canaryConfigSummary = application
    .getDataSource('canaryConfigs')
    .data.find((config: { id: string }) => config.id === canaryConfigId);

  return canaryConfigSummary && canaryConfigSummary.name;
}

function resolveControlAndExperimentNames(
  stage: IExecutionStage,
  runCanaryTasks: IExecutionStage[],
  firstScopeName: string,
) {
  if (stage.context.analysisType === KayentaAnalysisType.RealTimeAutomatic) {
    return {
      resolvedControl: stage.outputs.controlScope,
      resolvedExperiment: stage.outputs.experimentScope,
    };
  }

  if (runCanaryTasks.length) {
    return {
      resolvedControl: get(runCanaryTasks[0], ['context', 'scopes', firstScopeName, 'controlScope', 'scope']),
      resolvedExperiment: get(runCanaryTasks[0], ['context', 'scopes', firstScopeName, 'experimentScope', 'scope']),
    };
  }

  return {
    resolvedControl: get(stage, 'context.canaryConfig.scopes[0].controlScope'),
    resolvedExperiment: get(stage, 'context.canaryConfig.scopes[0].experimentScope'),
  };
}

function canaryConfigHref(id: string) {
  return ReactInjector.$state.href('home.applications.application.canary.canaryConfig.configDetail', { id });
}

KayentaStageExecutionDetails.title = 'canarySummary';
KayentaStageExecutionConfigDetails.title = 'canaryConfig';
