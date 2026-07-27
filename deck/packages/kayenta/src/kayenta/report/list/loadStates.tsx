import * as React from 'react';
import { connect } from 'react-redux';

import LoadStatesBuilder from '../../components/loadStates';
import CenteredDetail from '../../layout/centeredDetail';
import type { ICanaryState } from '../../reducers';
import type { AsyncRequestState } from '../../reducers/asyncRequest';
import ExecutionListTable from './table';

interface IExecutionListLoadStatesStateProps {
  loadState: AsyncRequestState;
}

const ExecutionListLoadStates = ({ loadState }: IExecutionListLoadStatesStateProps) => {
  const LoadStates = new LoadStatesBuilder()
    .onFulfilled(<ExecutionListTable />)
    .onFailed(
      <CenteredDetail>
        <h3 className="heading-3">Could not load canary execution history.</h3>
      </CenteredDetail>,
    )
    .build();
  return <LoadStates state={loadState} />;
};

const mapStateToProps = (state: ICanaryState) => {
  return {
    loadState: state.data.executions.load,
  };
};

export default connect(mapStateToProps)(ExecutionListLoadStates);
