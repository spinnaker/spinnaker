import * as React from 'react';
import { connect } from 'react-redux';

import { ICanaryState } from 'kayenta/reducers';
import { AsyncRequestState } from 'kayenta/reducers/asyncRequest';
import CenteredDetail from 'kayenta/layout/centeredDetail';
import ReportDetail from './detail';
import LoadStatesBuilder from 'kayenta/components/loadStates';

interface IReportLoadStatesStateProps {
  loadState: AsyncRequestState;
}

const ReportLoadStates = ({ loadState }: IReportLoadStatesStateProps) => {
  const LoadStates = new LoadStatesBuilder()
    .onFulfilled(<ReportDetail/>)
    .onFailed(
      <CenteredDetail>
        <h3 className="heading-3">Could not load canary report.</h3>
      </CenteredDetail>
    ).build();

  return <LoadStates state={loadState}/>;
};

const mapStateToProps = (state: ICanaryState) => ({
  loadState: state.selectedRun.load,
});

export default connect(mapStateToProps)(ReportLoadStates);

