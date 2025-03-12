import { ICanaryExecutionStatusResult } from 'kayenta/domain/ICanaryExecutionStatusResult';
import { ICanaryState } from 'kayenta/reducers';
import * as React from 'react';
import { connect } from 'react-redux';

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
