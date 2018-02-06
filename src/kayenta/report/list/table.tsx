import * as React from 'react';
import { connect } from 'react-redux';
import { ITableColumn, Table } from 'kayenta/layout/table';
import { ICanaryState } from 'kayenta/reducers';
import { ICanaryExecutionStatusResult } from 'kayenta/domain';
import FormattedDate from 'kayenta/layout/formattedDate';
import Score from '../detail/score';

// TODO(dpeach): fill these in.
const columns: ITableColumn<ICanaryExecutionStatusResult>[] = [
  {
    label: 'Config',
    getContent: execution => <span>{execution.result.config.name}</span>,
    width: 1,
  },
  {
    label: 'Score',
    getContent: execution => <Score score={execution.result.judgeResult.score}/>,
    width: 1,
  },
  {
    label: 'Start Time',
    getContent: execution => <FormattedDate dateIso={execution.startTimeIso}/>,
    width: 1,
  },
  {
    label: 'Status',
    getContent: execution => <span>{execution.status}</span>,
    width: 1,
  },
  {
    label: 'Pipeline',
    getContent: execution => <span>{execution.result.parentPipelineExecutionId}</span>,
    width: 1,
  },
  {
    getContent: () => <span>Report</span>,
    width: 1,
  }
];

interface IExecutionListTableStateProps {
  executions: ICanaryExecutionStatusResult[];
}

const ExecutionListTable = ({ executions }: IExecutionListTableStateProps) => {
  return (
    <Table
      rows={executions}
      columns={columns}
      rowKey={execution => execution.result.pipelineId}
    />
  );
};

const mapStateToProps = (state: ICanaryState) => ({
  executions: state.data.executions.data,
});

export default connect(mapStateToProps)(ExecutionListTable);
