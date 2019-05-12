import * as React from 'react';
import {
  OrdinalFrame,
  Annotation,
  IOrSummaryPiece,
  IOrFrameHoverArgs,
  IAnnotationType,
  ISemioticAnnotationArgs,
  IOrGroup,
} from 'semiotic';
import { extent } from 'd3-array';
import { Node, Force } from 'labella';

import * as utils from './utils';
import { vizConfig } from './config';
import { ISemioticChartProps, IMargin, ITooltip } from './semiotic.service';
import ChartHeader from './chartHeader';
import ChartLegend from './chartLegend';
import './boxplot.less';
import Tooltip from './tooltip';
import CircleIcon from './circleIcon';

interface IChartDataPoint {
  value: number;
  group: string;
  color: string;
}

interface IHoverData {
  value: number;
  label: string;
}

interface IHoverDataGroup {
  [group: string]: IHoverData[];
}

interface IAnnotationData {
  group: string;
  summaryKeys: string[];
  type: string;
}

interface IBoxPlotState {
  tooltip: ITooltip;
}

export default class BoxPlot extends React.Component<ISemioticChartProps, IBoxPlotState> {
  public state: IBoxPlotState = {
    tooltip: null,
  };

  private margin: IMargin = {
    top: 10,
    bottom: 20,
    left: 60,
    right: 40,
  };

  private decorateData = (dataPoints: number[], group: string): IChartDataPoint[] => {
    return dataPoints.map(dp => ({
      group,
      value: dp,
      color: vizConfig.colors[group],
    }));
  };

  private generateChartData = () => {
    const { metricSetPair } = this.props;
    const filterFunc = (v: IChartDataPoint) => typeof v.value === 'number';
    const baselineInput = this.decorateData(metricSetPair.values.control, 'baseline');
    const canaryInput = this.decorateData(metricSetPair.values.experiment, 'canary');
    const chartData = baselineInput.concat(canaryInput).filter(filterFunc);
    return chartData;
  };

  // Generate tooltip content that shows the summary statistics of a boxplot
  private createChartHoverHandler = () => {
    return (d: IOrFrameHoverArgs<IChartDataPoint> & IOrSummaryPiece): void => {
      if (d && d.type === 'frame-hover') {
        const points = d.points;
        const data: IHoverDataGroup = {
          baseline: [],
          canary: [],
        };

        points.forEach((p: IOrSummaryPiece) => {
          data[p.key].push({ label: p.label, value: p.value });
        });

        const summaryLabels = data.baseline.map((b: IHoverData) => b.label);
        const summaryKeysColumn = [
          <div className={'header'} key={'summary'}>
            Summary
          </div>,
          ...summaryLabels.map((label: string) => {
            return <div key={label}>{label}</div>;
          }),
        ];

        const baselineColumn = [
          <div className={'header'} key={'baseline'}>
            <CircleIcon group={'baseline'} />
            Baseline
          </div>,
          data.baseline.map((hd: IHoverData, i: number) => {
            return <div key={i}>{utils.formatMetricValue(hd.value)}</div>;
          }),
        ];

        const canaryColumn = [
          <div className="header" key="canary">
            <CircleIcon group="canary" />
            Canary
          </div>,
          data.canary.map((hd: IHoverData, i: number) => {
            return <div key={i}>{utils.formatMetricValue(hd.value)}</div>;
          }),
        ];

        const tooltipContent = (
          <div className="tooltip-container">
            <div className="columns">
              <div className="column">{summaryKeysColumn}</div>
              <div className="column">{baselineColumn}</div>
              <div className="column">{canaryColumn}</div>
            </div>
          </div>
        );

        this.setState({
          tooltip: {
            content: tooltipContent,
            x: d.x + this.margin.left,
            y: d.y + this.margin.top,
          },
        });
      } else {
        this.setState({ tooltip: null });
      }
    };
  };

  private defineAnnotations = () => {
    return ['baseline', 'canary'].map((g: string) => {
      return {
        type: 'summary-custom',
        group: g,
        summaryKeys: ['q1area', 'median', 'q3area'],
      };
    });
  };

  private customAnnotationFunction = (args: ISemioticAnnotationArgs<IAnnotationData, IOrGroup<IChartDataPoint>>) => {
    const {
      d,
      orFrameState: { pieceDataXY },
      categories,
    } = args;

    if (d.type === 'summary-custom') {
      // If no data exists for this group, don't return any annotation elements
      if (!categories[d.group]) {
        return null;
      }

      const summaryData = pieceDataXY.filter((sd: IOrSummaryPiece) => sd.key === d.group);
      const boxPlotWidth = categories[d.group].width;
      const statLabelMap: { [stat: string]: string } = {
        median: 'median',
        q1area: '25th %-ile',
        q3area: '75th %-ile',
      };
      const createNoteElement = (posY: number, dataPoint: IOrSummaryPiece) => {
        const name = dataPoint.summaryPieceName;
        const label = statLabelMap[name];
        const noteData = {
          x: dataPoint.x,
          y: posY,
          dx: boxPlotWidth / 2,
          dy: 0,
          note: {
            label: `${label}: ${utils.formatMetricValue(dataPoint.value)}`,
            wrap: 100,
            lineType: 'vertical',
            align: 'middle',
            orientation: 'topBottom',
            padding: 10,
          },
          className: 'boxplot-annotation',
        };
        return <Annotation key={name} noteData={noteData} />;
      };

      const nodes = d.summaryKeys
        .map((summaryKey: string) => {
          const pieceData = summaryData.find((sd: IOrSummaryPiece) => sd.summaryPieceName === summaryKey);
          return pieceData ? new Node<IOrSummaryPiece>(pieceData.y, 20, pieceData) : null;
        })
        .map((v: null | Node<IOrSummaryPiece>) => v) as Array<Node<IOrSummaryPiece>>;

      const forceOptions = {
        minPos: this.margin.top,
        maxPos: vizConfig.height - this.margin.bottom,
      };
      const annotations = new Force<IOrSummaryPiece>(forceOptions)
        .nodes(nodes)
        .compute()
        .nodes()
        .map((n: Node<IOrSummaryPiece>) => createNoteElement(n.currentPos, n.data));

      return annotations;
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
      summaryType: 'boxplot',
      oLabel: false,
      oPadding: 160,
      style: (d: IChartDataPoint) => {
        return {
          fill: d.color,
          fillOpacity: 0.7,
        };
      },
      summaryStyle: (d: IChartDataPoint) => {
        return {
          fill: d.color,
          fillOpacity: 0.4,
          stroke: '#6a6a6a',
          strokeWidth: 2,
        };
      },
      pieceClass: (d: IChartDataPoint) => `piece ${d.group}`,
      type: {
        type: 'swarm',
        r: 3,
        iterations: 50,
      },
      rExtent: extent(chartData.map(o => o.value)),
      customHoverBehavior: this.createChartHoverHandler(),
      hoverAnnotation: false,
      annotations: this.defineAnnotations(),
      summaryHoverAnnotation: [] as IAnnotationType[],
      data: chartData,
      oAccessor: (d: IChartDataPoint) => d.group,
      rAccessor: (d: IChartDataPoint) => d.value,
      svgAnnotationRules: this.customAnnotationFunction,
      summaryClass: 'boxplot-summary',
      axis: {
        orient: 'left',
        label: 'metric value',
        tickFormat: (d: number) => utils.formatMetricValue(d),
      },
    };
  };

  public render() {
    const { metricSetPair } = this.props;
    return (
      <div className="boxplot">
        <ChartHeader metric={metricSetPair.name} />
        <ChartLegend />
        <div className="graph-container">
          <div className="boxplot-chart">
            <OrdinalFrame {...this.getChartProps()} />
          </div>
          <Tooltip {...this.state.tooltip} />
        </div>
      </div>
    );
  }
}
