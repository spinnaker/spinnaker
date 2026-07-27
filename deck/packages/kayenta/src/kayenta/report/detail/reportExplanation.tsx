import * as React from 'react';
import { connect } from 'react-redux';

import type { ICanaryExecutionStatusResult } from '../../domain/ICanaryExecutionStatusResult';
import type { ICanaryState } from '../../reducers';

import './reportExplanation.less';

interface IReportMetadata {
  run: ICanaryExecutionStatusResult;
}

const getReason = (run: ICanaryExecutionStatusResult): string =>
  run?.result?.judgeResult?.score?.classificationReason ?? null;

const ReportExplanation = ({ run }: IReportMetadata) => {
  const classificationReason = getReason(run);

  if (classificationReason) {
    return (
      <section>
        <div className="report-explanation">{classificationReason}</div>
      </section>
    );
  }
  return null;
};

const mapStateToProps = (state: ICanaryState) => ({
  run: state.selectedRun.run,
});

export default connect(mapStateToProps)(ReportExplanation);
