import * as React from 'react';
import { round } from 'lodash';

import { IMetricResultScopeProps } from '../metricStoreConfig.service';
import FormattedDate from 'kayenta/layout/formattedDate';
import { ICanaryAnalysisResultsStats } from 'kayenta/domain';
import { ICanaryExecutionStatusResult } from 'kayenta/domain/ICanaryExecutionStatusResult';

import './metricResultScope.less';

interface ITableColumn {
  label: string;
  getValue: (target: string) => JSX.Element;
}

const getStats = (run: ICanaryExecutionStatusResult, metricName: string, target: string): ICanaryAnalysisResultsStats => {
  const result = run.result.judgeResult.results.find(r => r.name === metricName);
  if (target === 'experiment') {
    return result.experimentMetadata.stats;
  } else if (target === 'control') {
    return result.controlMetadata.stats;
  } else {
    return null;
  }
};

const Table = ({ tableColumns }: { tableColumns: ITableColumn[] }) => {
  return (
    <section className="horizontal space-between">
      <ul className="list-unstyled">
        <li>&nbsp;</li>
        <li>Baseline</li>
        <li>Canary</li>
      </ul>
      {
        tableColumns.map(column => (
          <ul className="list-unstyled" key={column.label}>
            <li
              className="uppercase label color-text-primary"
            >{column.label}
            </li>
            <li>{column.getValue('control')}</li>
            <li>{column.getValue('experiment')}</li>
          </ul>
        ))
      }
    </section>
  );
};

export default ({ metricConfig, metricSetPair, run }: IMetricResultScopeProps) => {
  const tableColumns: ITableColumn[] = [
    {
      label: 'Scope',
      getValue: target => {
        if (target === 'experiment') {
          return <span>{run.result.canaryExecutionRequest.experimentScope.scope}</span>
        } else if (target === 'control') {
          return <span>{run.result.canaryExecutionRequest.controlScope.scope}</span>
        } else {
          return null;
        }
      },
    },
    {
      label: 'Start',
      getValue: target => <FormattedDate dateIso={metricSetPair.scopes[target].startTimeIso}/>,
    },
    {
      label: 'Step',
      getValue: target => {
        const mins = metricSetPair.scopes[target].stepMillis / (1000 * 60);
        return <span>{mins} min{mins === 1 ? '' : 's'}</span>;
      }
    },
    {
      label: 'Count',
      getValue: target => <span>{getStats(run, metricConfig.name, target).count}</span>,
    },
    {
      label: 'Avg',
      getValue: target => <span>{round(getStats(run, metricConfig.name, target).mean, 2)}</span>,
    },
    {
      label: 'Max',
      getValue: target => <span>{round(getStats(run, metricConfig.name, target).max, 2)}</span>,
    },
    {
      label: 'Min',
      getValue: target => <span>{round(getStats(run, metricConfig.name, target).min, 2)}</span>,
    },
  ];

  return (
    <section className="stackdriver-metric-result">
      <label className="label uppercase color-text-primary">Name</label>
      <p>
        {metricConfig.name}
      </p>
      <label className="label uppercase color-text-primary">Metric Type</label>
      <p>
        {metricConfig.query.metricType}
      </p>
      <Table tableColumns={tableColumns}/>
    </section>
  );
};
