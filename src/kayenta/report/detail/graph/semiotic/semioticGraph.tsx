import * as React from 'react';
import ContainerDimensions from 'react-container-dimensions';

import { IMetricSetPairGraphProps, GraphType } from '../metricSetPairGraph.service';
import TimeSeries from './timeSeries';
import Histogram from './histogram';
import BoxPlot from './boxplot';
import NoValidDataSign from './noValidDataSign';
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
    const filterInvalidValues = (data: number[]) => data.filter(v => typeof v === 'number');

    if (filterInvalidValues(control).length === 0 && filterInvalidValues(experiment).length === 0) {
      return <NoValidDataSign />;
    }

    switch (type) {
      case GraphType.TimeSeries:
        return <TimeSeries {...chartProps} />;
        break;
      case GraphType.Histogram:
        return <Histogram {...chartProps} />;
        break;
      case GraphType.BoxPlot:
        return <BoxPlot {...chartProps} />;
        break;
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
