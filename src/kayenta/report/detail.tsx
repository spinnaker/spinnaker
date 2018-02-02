import * as React from 'react';

import ReportHeader from './header';
import ReportScores from './reportScores';
import MetricResults from './metricResults';

/*
* Layout for report detail view.
* */
export default () => (
  <div className="vertical flex-1">
    <ReportHeader/>
    <ReportScores/>
    <MetricResults/>
  </div>
);
