import * as React from 'react';
import { connect } from 'react-redux';
import { ITableColumn, Table } from 'kayenta/layout/table';
import { ICanaryState } from 'kayenta/reducers';
import { ICanaryExecutionStatusResult } from 'kayenta/domain';
import FormattedDate from 'kayenta/layout/formattedDate';
import CenteredDetail from 'kayenta/layout/centeredDetail';
import Score from '../detail/score';
import ReportLink from './reportLink';
import ConfigLink from './configLink';
import { PipelineLink } from './pipelineLink';

import './executionList.less';

const columns: ITableColumn<ICanaryExecutionStatusResult>[] = [
  {
    label: 'Config',
    getContent: execution => (
      <ConfigLink
        configName={execution.result.config.name}
        application={execution.application}
      />
    ),
    width: 1,
  },
  {
    label: 'Score',
    getContent: execution => (
      <Score
        score={execution.result.judgeResult.score}
        inverse={true}
        showClassification={false}
      />
    ),
    width: 1,
  },
  {
    label: 'Started',
    getContent: execution => <FormattedDate dateIso={execution.startTimeIso}/>,
    width: 1,
  },
  {
    getContent: execution => (
      <PipelineLink
        parentPipelineExecutionId={execution.result.parentPipelineExecutionId}
        application={execution.application}
      />
    ),
    width: 1,
  },
  {
    getContent: execution => (
      <ReportLink
        configName={execution.result.config.name}
        executionId={execution.result.pipelineId}
        application={execution.application}
      />
    ),
    width: 1,
  }
];

interface IExecutionListTableStateProps {
  executions: ICanaryExecutionStatusResult[];
}

const ExecutionListTable = ({ executions }: IExecutionListTableStateProps) => {
  if (!executions || !executions.length) {
    return (
      <CenteredDetail>
        <h3 className="heading-3">No canary execution history for this application.</h3>
      </CenteredDetail>
    );
  }

  return (
    <Table
      rows={executions}
      className="vertical flex-1 execution-list-table"
      columns={columns}
      rowKey={execution => execution.result.pipelineId}
      tableBodyClassName="flex-1"
    />
  );
};

const mapStateToProps = (state: ICanaryState) => ({
  executions: state.data.executions.data,
});

export default connect(mapStateToProps)(ExecutionListTable);
