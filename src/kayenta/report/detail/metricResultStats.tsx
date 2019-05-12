import * as React from 'react';
import { round } from 'lodash';
import { connect } from 'react-redux';

import { ICanaryAnalysisResultsStats } from 'kayenta/domain';
import { ICanaryExecutionStatusResult } from 'kayenta/domain/ICanaryExecutionStatusResult';
import { ICanaryMetricConfig } from 'kayenta/domain/ICanaryConfig';
import { IMetricSetPair } from 'kayenta/domain/IMetricSetPair';
import { runSelector, selectedMetricConfigSelector } from 'kayenta/selectors';
import { ICanaryState } from 'kayenta/reducers';
import FormattedDate from 'kayenta/layout/formattedDate';
import { ITableColumn, NativeTable } from 'kayenta/layout/table';

import './metricResultStats.less';

export interface IMetricResultStatsStateProps {
  metricConfig: ICanaryMetricConfig;
  metricSetPair: IMetricSetPair;
  run: ICanaryExecutionStatusResult;
  service: string;
}

const getStats = (run: ICanaryExecutionStatusResult, id: string, target: string): ICanaryAnalysisResultsStats => {
  const result = run.result.judgeResult.results.find(r => r.id === id);
  if (target === 'experiment') {
    return result.experimentMetadata.stats;
  } else if (target === 'control') {
    return result.controlMetadata.stats;
  } else {
    return null;
  }
};

interface IResultMetadataRow {
  label: string;
  getContent: () => JSX.Element;
}

const ResultMetadataRow = ({ row }: { row: IResultMetadataRow }) => {
  if (!row.getContent()) {
    return null;
  }

  return (
    <div>
      <label className="label uppercase color-text-primary">{row.label}</label>
      {row.getContent()}
    </div>
  );
};

const MetricResultStats = ({ metricConfig, metricSetPair, run }: IMetricResultStatsStateProps) => {
  const tableColumns: Array<ITableColumn<string>> = [
    {
      getContent: target => <span>{target === 'control' ? 'Baseline' : 'Canary'}</span>,
    },
    {
      label: 'start',
      getContent: target => <FormattedDate dateIso={metricSetPair.scopes[target].startTimeIso} />,
      hide: () => {
        const request = run.canaryExecutionRequest;
        const configuredControlStart = request.scopes[metricConfig.scopeName].controlScope.start;
        const actualControlStart = metricSetPair.scopes.control.startTimeIso;

        const configuredExperimentStart = request.scopes[metricConfig.scopeName].experimentScope.start;
        const actualExperimentStart = metricSetPair.scopes.experiment.startTimeIso;

        return configuredControlStart === actualControlStart && configuredExperimentStart === actualExperimentStart;
      },
    },
    {
      label: 'count',
      getContent: target => <span>{getStats(run, metricSetPair.id, target).count}</span>,
    },
    {
      label: 'avg',
      getContent: target => <span>{round(getStats(run, metricSetPair.id, target).mean, 3)}</span>,
    },
    {
      label: 'max',
      getContent: target => <span>{round(getStats(run, metricSetPair.id, target).max, 3)}</span>,
    },
    {
      label: 'min',
      getContent: target => <span>{round(getStats(run, metricSetPair.id, target).min, 3)}</span>,
    },
  ];

  const metadataRows: IResultMetadataRow[] = [
    {
      label: 'classification reason',
      getContent: () => {
        const result = run.result.judgeResult.results.find(r => r.id === metricSetPair.id);

        if (!result.classificationReason) {
          return null;
        }

        return <p>{result.classificationReason}</p>;
      },
    },
  ];

  return (
    <div className="metric-stats vertical">
      {metadataRows.map(row => (
        <ResultMetadataRow row={row} key={row.label} />
      ))}
      <NativeTable columns={tableColumns} rows={['control', 'experiment']} rowKey={row => row} />
    </div>
  );
};

const mapStateToProps = (state: ICanaryState): IMetricResultStatsStateProps => ({
  metricConfig: selectedMetricConfigSelector(state),
  metricSetPair: state.selectedRun.metricSetPair.pair,
  run: runSelector(state),
  service: selectedMetricConfigSelector(state).query.type,
});

export default connect(mapStateToProps)(MetricResultStats);
