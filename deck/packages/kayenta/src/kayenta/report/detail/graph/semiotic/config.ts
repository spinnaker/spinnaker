interface IColorConfig {
  [propName: string]: string;
}

interface ITimeSeriesConfig {
  [propName: string]: any;
}

interface IDataGroup {
  [group: string]: string;
}

interface IVizConfig {
  readonly colors: IColorConfig;
  readonly height: number;
  readonly timeSeries: ITimeSeriesConfig;
  readonly dataGroupMap: IDataGroup;
}

export const vizConfig: IVizConfig = {
  colors: {
    baseline: '#52b3d9',
    canary: '#e26a6a',
    background: '#f8f8f8',
  },
  height: 400,
  timeSeries: {
    minimapDataPointsThreshold: 240,
    minimapHeight: 40,
    axisTickLineHeight: 4,
    axisTickLabelHeight: 32,
    axisLabelHeight: 16,
  },
  dataGroupMap: {
    baseline: 'control',
    canary: 'experiment',
  },
};
