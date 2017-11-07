import * as React from 'react';

import Graph from './graph/graph';
import MetricResultScope from './metricResultScope';

/*
* Responsible for layout of the metric result detail view after the metric set
* pair for the result has loaded successfully.
* */
export default () => (
  <section>
    <Graph/>
    <MetricResultScope/>
  </section>
);
