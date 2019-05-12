import * as React from 'react';
import {
  OrdinalFrame,
  Annotation,
  IOrFrameHoverArgs,
  IOrPiece,
  IOrGroup,
  IOrXyData,
  ISemioticAnnotationArgs,
  IAnnotationType,
} from 'semiotic';
import { histogram, extent } from 'd3-array';
import { scaleLinear } from 'd3-scale';

import * as utils from './utils';
import { vizConfig } from './config';
import { ISemioticChartProps, IMargin, ITooltip } from './semiotic.service';
import './histogram.less';
import ChartHeader from './chartHeader';
import ChartLegend from './chartLegend';
import Tooltip from './tooltip';
import CircleIcon from './circleIcon';

interface IInputDataPoint {
  value: number;
  group: string;
}

interface IChartDataPoint {
  group: string;
  count: number;
  x0: number;
  x1: number;
}

interface IAnnotationData extends IChartDataPoint {
  type: string;
}

interface IHistogramState {
  tooltip: ITooltip;
}

export default class Histogram extends React.Component<ISemioticChartProps, IHistogramState> {
  public state: IHistogramState = {
    tooltip: null,
  };

  private margin: IMargin = {
    top: 20,
    bottom: 20,
    left: 60,
    right: 10,
  };

  private decorateData = (dataPoints: number[], group: string): IInputDataPoint[] => {
    return dataPoints.map(dp => ({
      group,
      value: dp,
    }));
  };

  /*
  * Semiotic actually supports histogram as a "summary type" out of the box, but the customization is
  * currently limited (e.g. it can't display the y-axis ticks & labels)
  * Hence we're manually generating histogram data using D3 and display it as
  * a grouped bar chart in semiotic
  */
  private generateChartData = () => {
    const { metricSetPair } = this.props;
    const filterFunc = (v: IInputDataPoint) => typeof v.value === 'number';
    const baselineInput = this.decorateData(metricSetPair.values.control, 'baseline');
    const canaryInput = this.decorateData(metricSetPair.values.experiment, 'canary');
    const combinedInput = baselineInput.concat(canaryInput).filter(filterFunc);

    const x = scaleLinear()
      .domain(extent(combinedInput.map(o => o.value)))
      .nice();
    const domain = x.domain() as [number, number];

    // create histogram bins based on the combined data points
    const histogramData = histogram<IInputDataPoint, number>()
      .domain(domain)
      .value((d: IInputDataPoint) => d.value)(combinedInput);

    const chartData: IChartDataPoint[] = [];

    // Convert it to ordinal data format for bar chart in semiotic
    histogramData.forEach(h => {
      const { x0, x1 } = h;
      const baselineBin = { group: 'baseline', x0, x1, count: 0 };
      const canaryBin = { group: 'canary', x0, x1, count: 0 };
      h.forEach(d => (d.group === 'baseline' ? baselineBin.count++ : canaryBin.count++));
      chartData.push(baselineBin);
      chartData.push(canaryBin);
    });

    return chartData;
  };

  // Function factory to handle hover event
  private createChartHoverHandler = (chartData: IChartDataPoint[]) => {
    return (d: IOrFrameHoverArgs<IChartDataPoint>): void => {
      if (d && d.type === 'column-hover') {
        const x1Max: number = Math.max(...chartData.map((cd: IChartDataPoint) => cd.x1));
        const xyData = d.column.xyData;
        const x = xyData[1].xy.x + this.margin.left;
        const halfHeight1 = xyData[0].xy.height / 2;
        const halfHeight2 = xyData[1].xy.height / 2;
        const y = vizConfig.height - this.margin.bottom - Math.min(halfHeight1, halfHeight2);
        const { x0, x1 } = d.summary[0].data;
        const tooltipRows = d.summary.map((s: IOrPiece<IChartDataPoint>) => {
          const { group, count } = s.data;
          const valueStyle = {
            fontWeight: 'bold',
          } as React.CSSProperties;

          return (
            <div key={group}>
              <CircleIcon group={group} />
              <span>{` ${group} count: `}</span>
              <span style={valueStyle}>{count}</span>
            </div>
          );
        });

        let label = `For metric value more than / equal to ${utils.formatMetricValue(x0)} `;
        label +=
          x1 === x1Max
            ? `and less than / equal to ${utils.formatMetricValue(x1)}`
            : `and less than ${utils.formatMetricValue(x1)}`;

        const tooltipContent = (
          <div>
            <div>{label}</div>
            {tooltipRows}
          </div>
        );

        this.setState({
          tooltip: {
            content: tooltipContent,
            x,
            y,
          },
        });
      } else {
        this.setState({ tooltip: null });
      }
    };
  };

  // generate bar value annotations object
  private defineAnnotations = (chartData: IChartDataPoint[]) => {
    const annotations = [] as IAnnotationData[];
    chartData.forEach((d: IChartDataPoint) => {
      if (d.count > 0) {
        annotations.push({
          type: 'bar-value-custom',
          ...d,
        });
      }
    });
    return annotations;
  };

  // function to actually create the JSX elements based on the annotation object
  private customAnnotationFunction = (
    args: ISemioticAnnotationArgs<IAnnotationData, IOrGroup<IChartDataPoint>>,
  ): JSX.Element | null => {
    const { d, i, categories } = args;
    if (d.type === 'bar-value-custom') {
      const { x, y, width } = categories[d.x1].xyData.find(
        (c: IOrXyData<IChartDataPoint>) => c.piece.data.group === d.group,
      ).xy;
      const noteData = {
        x: x + width / 2,
        y,
        nx: x + width / 2,
        ny: y - 1,
        note: {
          label: `${d.count}`,
          wrap: 100,
          align: 'middle',
          orientation: 'topBottom',
          padding: 0,
          color: vizConfig.colors[d.group],
          lineType: 'horizontal',
        },
        className: `bar-annotation`,
      };
      return <Annotation key={i} noteData={noteData} />;
    } else {
      return null;
    }
  };

  private getChartProps = () => {
    const { parentWidth } = this.props;
    const chartData = this.generateChartData();

    return {
      size: [parentWidth, vizConfig.height],
      margin: this.margin,
      projection: 'vertical',
      type: 'clusterbar',
      oLabel: (v: string) => <text textAnchor="middle">{utils.formatMetricValue(parseFloat(v))}</text>,
      oPadding: 20,
      oAccessor: (d: IChartDataPoint) => d.x1,
      style: (d: IChartDataPoint) => {
        return {
          fill: vizConfig.colors[d.group],
          strokeWidth: 1,
          stroke: vizConfig.colors.background,
          strokeOpacity: 1,
        };
      },
      customHoverBehavior: this.createChartHoverHandler(chartData),
      data: this.generateChartData(),
      axis: [
        {
          orient: 'left',
          label: 'measurement count',
          tickFormat: (d: number) => (d === 0 ? null : Math.abs(d)),
        },
      ],
      rAccessor: (d: IChartDataPoint) => d.count,
      annotations: this.defineAnnotations(chartData),
      svgAnnotationRules: this.customAnnotationFunction,
      hoverAnnotation: [] as IAnnotationType[],
    };
  };

  public render() {
    const { metricSetPair } = this.props;

    return (
      <div className="histogram">
        <ChartHeader metric={metricSetPair.name} />
        <ChartLegend />
        <div className="graph-container">
          <div className="canary-chart">
            <OrdinalFrame {...this.getChartProps()} />
          </div>
          <Tooltip {...this.state.tooltip} />
        </div>
      </div>
    );
  }
}
