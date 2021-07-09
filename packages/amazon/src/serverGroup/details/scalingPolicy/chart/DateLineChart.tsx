import {
  CartesianScaleOptions,
  Chart,
  ChartConfiguration,
  ChartDataset,
  Filler,
  Legend,
  LinearScale,
  LinearScaleOptions,
  LineController,
  LineElement,
  PointElement,
  TimeScale,
  TimeScaleOptions,
  Title,
  Tooltip,
  TooltipItem,
} from 'chart.js';
import 'chartjs-adapter-luxon';
import * as React from 'react';

Chart.register(LineController, LineElement, PointElement, LinearScale, Title, TimeScale, Tooltip, Legend, Filler);

interface IDateSeriesTuple {
  x: Date;
  y: number;
}

export type IDateLine = ChartDataset<'line', IDateSeriesTuple[]>;

export interface IDateLineChartProps {
  lines: IDateLine[];
  style?: object;
  className?: string;
}

function makeChart(canvasElement: HTMLCanvasElement) {
  const xScaleOptions: TimeScaleOptions & any = {
    type: 'time',
    display: true,
    scaleLabel: {
      labelString: 'Date',
    },
    ticks: {
      major: { enabled: true },
    },
  };

  const yScaleOptions: Partial<CartesianScaleOptions & LinearScaleOptions> = {
    min: 0,
    stacked: true,
  };

  const chartConfig: ChartConfiguration<'line'> = {
    type: 'line',
    data: {
      labels: [],
      datasets: [{ data: [] }],
    },
    options: {
      plugins: {
        tooltip: {
          mode: 'index',
          itemSort: (a: TooltipItem<any>, b: TooltipItem<any>) => {
            const labels = a.chart.data.datasets.map((d) => d.label);
            const labelA = (a.dataset as { label?: string }).label ?? labels[0];
            const labelB = (b.dataset as { label?: string }).label ?? labels[0];
            return labels.indexOf(labelB) - labels.indexOf(labelA);
          },
        },
      },
      hover: {
        mode: 'index',
      },
      elements: {
        point: {
          hitRadius: 12,
          radius: 1,
          hoverRadius: 5,
        },
      },
      scales: {
        x: xScaleOptions,
        y: yScaleOptions,
      },
    },
  };

  return new Chart(canvasElement, chartConfig);
}

export function DateLineChart(props: IDateLineChartProps) {
  const chartContainerRef = React.useRef<HTMLCanvasElement>();
  const chartRef = React.useRef<Chart<'line', Array<{ x: Date; y: number }>>>();
  const { lines, className = '', style = {} } = props;

  React.useEffect(() => {
    if (chartContainerRef) {
      chartRef.current = makeChart(chartContainerRef.current) as any;
      return () => chartRef.current?.destroy();
    }
    return null;
  }, [chartContainerRef.current]);

  React.useEffect(() => {
    const chart = chartRef.current;
    if (chart && lines) {
      chart.options.animation = false;
      chart.data.datasets = lines;
      chart.update();
    }
  }, [chartRef.current, lines]);

  return <canvas ref={chartContainerRef} {...style} className={className} />;
}
