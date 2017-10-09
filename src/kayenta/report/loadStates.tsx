import * as React from 'react';
import { connect } from 'react-redux';

import { NgReact } from '@spinnaker/core';

import { ICanaryState } from '../reducers/index';
import { AsyncRequestState } from '../reducers/asyncRequest';
import { ICanaryJudgeResult } from '../domain/ICanaryJudgeResult';
import CenteredDetail from '../layout/centeredDetail';

interface IReportLoadStatesStateProps {
  loadState: AsyncRequestState;
  report: ICanaryJudgeResult;
}

const ReportLoadStates = ({ loadState, report }: IReportLoadStatesStateProps) => {
  switch (loadState) {
    case AsyncRequestState.Requesting:
      return (
        <div className="spinner">
          <NgReact.Spinner radius={20} width={3} length={20}/>
        </div>
      );

    case AsyncRequestState.Fulfilled:
      return (
        <pre>{JSON.stringify(report, null, 2)}</pre>
      );

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
  report: state.selectedReport.report,
});

export default connect(mapStateToProps)(ReportLoadStates);

