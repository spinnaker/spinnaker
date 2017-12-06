import * as React from 'react';

import Graph from './graph/graph';
import MetricResultStats from './metricResultStats';

import './metricResultDetailLayout.less';

/*
* Responsible for layout of the metric result detail view after the metric set
* pair for the result has loaded successfully.
* */
export default () => (
  <section className="vertical result-detail-layout">
    <Graph/>
    <MetricResultStats/>
  </section>
);
