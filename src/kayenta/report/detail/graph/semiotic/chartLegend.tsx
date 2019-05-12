import * as React from 'react';
import * as classNames from 'classnames';

import { vizConfig } from './config';
import './chartLegend.less';

interface IChartLegendProps {
  isClickable?: boolean;
  onClickHandler?: (group: string) => void;
  showGroup?: { [group: string]: boolean };
}

export default (props: IChartLegendProps) => {
  const { onClickHandler, showGroup = { baseline: true, canary: true }, isClickable = false } = props;

  const baselineIconStyle = {
    backgroundColor: vizConfig.colors.baseline,
  };

  const canaryIconStyle = {
    backgroundColor: vizConfig.colors.canary,
  };

  const handleClick = (group: string) => (onClickHandler ? () => onClickHandler(group) : undefined);

  const legendItemClass = classNames('legend-item', { clickable: isClickable });
  const legendItemClassCanary = classNames(legendItemClass, { deselected: !showGroup.canary });
  const legendItemClassBaseline = classNames(legendItemClass, { deselected: !showGroup.baseline });
  return (
    <div className="chart-legend">
      <div className={legendItemClassBaseline} onClick={handleClick('baseline')}>
        <div className="legend-icon" style={baselineIconStyle} />
        <div>Baseline</div>
      </div>
      <div className={legendItemClassCanary} onClick={handleClick('canary')}>
        <div className="legend-icon" style={canaryIconStyle} />
        <div>Canary</div>
      </div>
    </div>
  );
};
