import * as React from 'react';

export interface IChartHeaderProps {
  metric: string;
}

export default ({ metric }: IChartHeaderProps) => {
  return (
    <div className="chart-header">
      <h6 className="heading-6 color-text-primary">
        <span className="uppercase prefix">metric name:</span>
        <span>
          &nbsp;
          {metric}
        </span>
      </h6>
    </div>
  );
};
