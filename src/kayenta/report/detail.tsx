import * as React from 'react';

import ReportHeader from './header';
import ReportScores from './reportScores';
import MetricResults from './metricResults';

/*
* Layout for report detail view.
* */
export default () => (
  <div>
    <ReportHeader/>
    <ReportScores/>
    <MetricResults/>
  </div>
);
