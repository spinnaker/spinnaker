import * as React from 'react';

import { vizConfig } from './config';

export interface ICircleIconProps {
  group: string;
}

// simple component to show circle icons in tooltips
export default ({ group }: ICircleIconProps) => {
  const iconStyle = {
    color: vizConfig.colors[group],
    paddingRight: 4,
  };
  return <i className="fas fa-circle" style={iconStyle} />;
};
