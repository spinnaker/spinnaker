import * as React from 'react';
import ContainerDimensions from 'react-container-dimensions';

import BoxPlot from './boxplot';
import Histogram from './histogram';
import { GraphType, IMetricSetPairGraphProps } from '../metricSetPairGraph.service';
import NoValidDataSign from './noValidDataSign';
import TimeSeries from './timeSeries';

import './semioticGraph.less';

export default class SemioticGraph extends React.Component<IMetricSetPairGraphProps> {
  private fetchChart = (parentWidth: number) => {
    const {
      type,
      metricSetPair: {
        values: { control, experiment },
      },
    } = this.props;
    const chartProps = {
      ...this.props,
      parentWidth,
    };
    const filterInvalidValues = (data: number[]) => data.filter((v) => typeof v === 'number');

    if (filterInvalidValues(control).length === 0 && filterInvalidValues(experiment).length === 0) {
      return <NoValidDataSign />;
    }

    switch (type) {
      case GraphType.TimeSeries:
        return <TimeSeries {...chartProps} />;
      case GraphType.Histogram:
        return <Histogram {...chartProps} />;
      case GraphType.BoxPlot:
        return <BoxPlot {...chartProps} />;
      default:
        return null;
    }
  };

  public render() {
    return (
      <div className="semiotic-graph">
        <ContainerDimensions>{({ width }: { width: number }) => this.fetchChart(width)}</ContainerDimensions>
      </div>
    );
  }
}
