import { ICanaryExecutionStatusResult } from 'kayenta/domain';
import { ICanaryState } from 'kayenta/reducers';
import * as React from 'react';
import { connect } from 'react-redux';

import ReportHeader from './header';
import MetricResults from './metricResults';
import ReportException from './reportException';
import ReportExplanation from './reportExplanation';
import ReportScores from './reportScores';

import './detail.less';

/*
 * Layout for report detail view.
 * */

interface IDetailViewProps {
  run: ICanaryExecutionStatusResult;
}

function DetailView({ run }: IDetailViewProps) {
  const [isExpanded, setIsExpanded] = React.useState<boolean>(true);

  if (!run.result?.judgeResult.results?.length && run.exception) {
    return <ReportException />;
  }

  const toggleDetailHeader = () => {
    setIsExpanded(!isExpanded);
  };
  return (
    <>
      <div className="vertical flex-1">
        {isExpanded && <ReportHeader />}
        <ReportExplanation />
        <ReportScores isExpanded={isExpanded} toggleHeader={toggleDetailHeader} />
        <MetricResults />
      </div>
    </>
  );
}

const mapStateToProps = (state: ICanaryState) => ({
  run: state.selectedRun.run,
});

export default connect(mapStateToProps)(DetailView);
