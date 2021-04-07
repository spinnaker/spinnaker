import * as React from 'react';
import { Suspense } from 'react';

import { Spinner } from '@spinnaker/core';

import { GraphType, IMetricSetPairGraphProps, metricSetPairGraphService } from '../metricSetPairGraph.service';
const SemioticGraphLazy = React.lazy(() => import(/* webpackChunkName: "Lazy-KayentaGraphs" */ './semioticGraph'));

const supportedGraphTypes: GraphType[] = [GraphType.TimeSeries, GraphType.Histogram, GraphType.BoxPlot];
// Semiotic component registration
metricSetPairGraphService.register({
  name: 'semiotic',
  handlesGraphType: (type) => supportedGraphTypes.includes(type),
  getGraph: () => (props: IMetricSetPairGraphProps) => (
    <Suspense fallback={<Spinner />}>
      <SemioticGraphLazy {...props} />
    </Suspense>
  ),
});
