import * as React from 'react';
import { connect } from 'react-redux';

import { NgReact } from '@spinnaker/core';

import { ICanaryState } from '../reducers/index';
import { AsyncRequestState } from '../reducers/asyncRequest';
import CenteredDetail from '../layout/centeredDetail';
import ReportDetail from './detail';

interface IReportLoadStatesStateProps {
  loadState: AsyncRequestState;
}

const ReportLoadStates = ({ loadState }: IReportLoadStatesStateProps) => {
  switch (loadState) {
    case AsyncRequestState.Requesting:
      return (
        <div className="spinner">
          <NgReact.Spinner radius={20} width={3} length={20}/>
        </div>
      );

    case AsyncRequestState.Fulfilled:
      return <ReportDetail/>;

    case AsyncRequestState.Failed:
      return (
        <CenteredDetail>
          <h3 className="heading-3">Could not load canary report.</h3>
        </CenteredDetail>
      );
  }
};

const mapStateToProps = (state: ICanaryState) => ({
  loadState: state.selectedReport.load,
});

export default connect(mapStateToProps)(ReportLoadStates);

