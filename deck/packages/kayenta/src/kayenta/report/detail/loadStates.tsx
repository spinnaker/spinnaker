import * as React from 'react';
import { connect } from 'react-redux';

import LoadStatesBuilder from '../../components/loadStates';
import ReportDetail from './detail';
import CenteredDetail from '../../layout/centeredDetail';
import type { ICanaryState } from '../../reducers';
import type { AsyncRequestState } from '../../reducers/asyncRequest';

interface IReportLoadStatesStateProps {
  loadState: AsyncRequestState;
}

const ReportLoadStates = ({ loadState }: IReportLoadStatesStateProps) => {
  const LoadStates = new LoadStatesBuilder()
    .onFulfilled(<ReportDetail />)
    .onFailed(
      <CenteredDetail>
        <h3 className="heading-3">Could not load canary report.</h3>
      </CenteredDetail>,
    )
    .build();

  return <LoadStates state={loadState} />;
};

const mapStateToProps = (state: ICanaryState) => ({
  loadState: state.selectedRun.load,
});

export default connect(mapStateToProps)(ReportLoadStates);
