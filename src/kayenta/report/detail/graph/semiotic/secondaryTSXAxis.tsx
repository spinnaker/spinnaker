import * as React from 'react';
import { scaleUtc } from 'd3-scale';
import { Axis } from 'semiotic';

import { IMargin } from './semiotic.service';
import * as utils from './utils';
import CustomAxisTickLabel from './customAxisTickLabel';
import './secondaryTSXAxis.less';
import { vizConfig } from './config';

interface ISecondaryTSXAxisProps {
  margin: IMargin;
  width: number;
  millisSet: number[];
  axisLabel?: string;
  bottomOffset: number;
}

/*
* Secondary X Axis for Time Series Graph
* Used when canary and baseline have different start time. We can overlay this axis component
* on the main graph component
*/
export default class SecondaryTSXAxis extends React.Component<ISecondaryTSXAxisProps> {
  public render() {
    const { margin, width, millisSet, axisLabel, bottomOffset } = this.props;

    const { axisTickLineHeight, axisTickLabelHeight, axisLabelHeight } = vizConfig.timeSeries;

    const extent = [millisSet[0], millisSet[millisSet.length - 1]].map((ms: number) => new Date(ms));
    const totalAxisHeight = axisTickLabelHeight + axisTickLineHeight + (axisLabel ? axisLabelHeight : 0);
    const netWidth = width - margin.left - margin.right;
    const range = [0, netWidth];
    const containerStyle = {
      bottom: bottomOffset,
    };

    const svgWrapperStyle = {
      transform: `translateX(${margin.left}px)`,
    };
    return (
      <svg className="axis secondary-ts-x-axis" width={width} height={totalAxisHeight} style={containerStyle}>
        <g className="wrapper" style={svgWrapperStyle}>
          <Axis
            className="x axis bottom canary-dual-axis"
            size={[netWidth, axisTickLineHeight]}
            scale={scaleUtc()
              .domain(extent)
              .range(range)}
            orient="bottom"
            label="Canary"
            tickValues={utils.calculateDateTimeTicks(millisSet)}
            tickFormat={(d: number) => <CustomAxisTickLabel millis={d} />}
          />
        </g>
      </svg>
    );
  }
}
