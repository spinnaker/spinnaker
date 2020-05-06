import * as React from 'react';
import { connect } from 'react-redux';

import { ICanaryState } from 'kayenta/reducers';
import { ICanaryExecutionStatusResult } from 'kayenta/domain/ICanaryExecutionStatusResult';

import './reportExplanation.less';

interface IReportMetadata {
  run: ICanaryExecutionStatusResult;
}

const getReason = (run: ICanaryExecutionStatusResult): string => {
  if (run && run.result && run.result.judgeResult && run.result.judgeResult.score) {
    return run.result.judgeResult.score.classificationReason;
  }
  return null;
};

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
