import * as React from 'react';
import { chain } from 'lodash';

import { ICanaryAnalysisResult } from 'kayenta/domain/ICanaryJudgeResult';
import { Table, ITableColumn } from 'kayenta/layout/table';
import MetricResultClassification from './metricResultClassification';

export const MultipleResultsTable = ({ results }: { results: ICanaryAnalysisResult[] }) => {
  const tagKeys = chain(results)
    .flatMap(r => Object.keys(r.tags || {}))
    .uniq()
    .value();

  let columns: ITableColumn<ICanaryAnalysisResult>[] = tagKeys.map(key => ({
    label: key,
    width: 1,
    getContent: (result: ICanaryAnalysisResult) => <span>{result.tags[key]}</span>,
  }));

  columns = columns.concat({
    width: 1,
    getContent: (result: ICanaryAnalysisResult) => (
      <div className="pull-right">
        <MetricResultClassification classification={result.classification}/>
      </div>
    ),
  });

  return (
    <Table
      rows={results}
      columns={columns}
      className="multiple-results-table"
      rowClassName={() => 'horizontal'}
      rowKey={r => Object.entries(r.tags || {}).map(([key, value]) => `${key}:${value}`).join(':')}
    />
  );
};
