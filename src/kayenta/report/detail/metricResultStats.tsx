import * as React from 'react';
import { round } from 'lodash';
import { connect } from 'react-redux';

import { CanarySettings } from 'kayenta/canary.settings';
import { ICanaryAnalysisResultsStats } from 'kayenta/domain';
import { ICanaryExecutionStatusResult } from 'kayenta/domain/ICanaryExecutionStatusResult';
import { ICanaryMetricConfig } from 'kayenta/domain/ICanaryConfig';
import { IMetricSetPair } from 'kayenta/domain/IMetricSetPair';
import { runSelector, selectedMetricConfigSelector } from 'kayenta/selectors';
import { ICanaryState } from 'kayenta/reducers';
import metricStoreConfigStore from 'kayenta/metricStore/metricStoreConfig.service';
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

const buildAtlasGraphUrl = (metricSetPair: IMetricSetPair) => {
  const { attributes, scopes, values } = metricSetPair;
  const { atlasGraphBaseUrl } = CanarySettings;

  // TODO: If the control and experiment have different baseURLs, generate two links instead of a combined one.
  const backend = encodeURIComponent(attributes.control.baseURL);
  const query = `${attributes.experiment.query},Canary,:legend,:freeze,${attributes.control.query},Baseline,:legend`;

  const startTime = Math.min(scopes.control.startTimeMillis, scopes.experiment.startTimeMillis);
  const controlEndTime = scopes.control.startTimeMillis + values.control.length * scopes.control.stepMillis;
  const experimentEndTime = scopes.experiment.startTimeMillis + values.experiment.length * scopes.experiment.stepMillis;
  const endTime = Math.max(controlEndTime, experimentEndTime);

  return `${atlasGraphBaseUrl}?backend=${backend}&g.q=${query}&g.s=${startTime}&g.e=${endTime}&g.w=651&mode=png&axis=0`;
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

const MetricResultStats = ({ metricConfig, metricSetPair, run, service }: IMetricResultStatsStateProps) => {
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
      getContent: target => <span>{round(getStats(run, metricSetPair.id, target).mean, 2)}</span>,
    },
    {
      label: 'max',
      getContent: target => <span>{round(getStats(run, metricSetPair.id, target).max, 2)}</span>,
    },
    {
      label: 'min',
      getContent: target => <span>{round(getStats(run, metricSetPair.id, target).min, 2)}</span>,
    },
  ];

  const metadataRows: IResultMetadataRow[] = [
    {
      label: 'name',
      getContent: () => <p>{metricConfig.name}</p>,
    },
    {
      label: 'explore data',
      getContent: () => {
        if (service !== 'atlas') {
          return null;
        }

        return (
          <p>
            <a className="small" href={buildAtlasGraphUrl(metricSetPair)} target="_blank">
              Atlas UI
            </a>
          </p>
        );
      },
    },
    {
      label: 'query',
      getContent: () => <p>{metricStoreConfigStore.getDelegate(metricConfig.query.type).queryFinder(metricConfig)}</p>,
    },
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
