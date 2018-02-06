import * as React from 'react';
import { connect } from 'react-redux';
import { ICanaryState } from 'kayenta/reducers';
import { AsyncRequestState } from 'kayenta/reducers/asyncRequest';
import LoadStatesBuilder from 'kayenta/components/loadStates';
import ExecutionListTable from './table';

interface IExecutionListLoadStatesStateProps {
  loadState: AsyncRequestState;
}

const ExecutionListLoadStates = ({ loadState }: IExecutionListLoadStatesStateProps) => {
  const LoadStates = new LoadStatesBuilder()
    .onFulfilled(
      <ExecutionListTable/>
    ).onFailed(
      <h3 className="heading-3 text-center">Could not load canary execution history.</h3>
    ).build();
  return <LoadStates state={loadState}/>;
};

const mapStateToProps = (state: ICanaryState) => {
  return {
    loadState: state.data.executions.load,
  };
};

export default connect(mapStateToProps)(ExecutionListLoadStates);
