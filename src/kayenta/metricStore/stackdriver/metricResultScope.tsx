import * as React from 'react';
import { round } from 'lodash';

import { IExecution } from '@spinnaker/core';
import { IMetricResultScopeProps } from '../metricStoreConfig.service';
import FormattedDate from 'kayenta/layout/formattedDate';
import { CANARY_JUDGE } from 'kayenta/service/run/canaryRunStages';
import { ICanaryJudgeStage, ICanaryAnalysisResultsStats } from 'kayenta/domain';
import {
  IStackdriverFetchStage,
  STACKDRIVER_FETCH_STAGE
} from './domain/IStackdriverFetchStage';

import './metricResultScope.less';

interface ITableColumn {
  label: string;
  getValue: (target: string) => JSX.Element;
}

const getStats = (run: IExecution, metricName: string, target: string): ICanaryAnalysisResultsStats => {
  const canaryJudgeStage = run.stages.find(s => s.type === CANARY_JUDGE) as ICanaryJudgeStage;
  if (target === 'experiment') {
    return canaryJudgeStage.context.result.results[metricName].experimentMetadata.stats;
  } else if (target === 'control') {
    return canaryJudgeStage.context.result.results[metricName].controlMetadata.stats;
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
        const fetchStage = run.stages.find(s =>
          s.type === STACKDRIVER_FETCH_STAGE
            && s.name.toLowerCase().includes(target) // TODO(dpeach): This is a monstrosity. Move scope into the metric set pair.
        ) as IStackdriverFetchStage;
        return <span>{fetchStage.context.stackdriverCanaryScope.scope}</span>;
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
