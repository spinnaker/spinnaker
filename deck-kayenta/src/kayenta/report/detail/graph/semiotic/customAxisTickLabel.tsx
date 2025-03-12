import * as React from 'react';

import * as utils from './utils';

export interface ICustomAxisTickLabel {
  millis: number;
}

// custom labels as we want two label rows when showing date + hour at midnight
export default ({ millis }: ICustomAxisTickLabel) => {
  const text = utils.dateTimeTickFormatter(millis).map((s: string, i: number) => (
    <text textAnchor={'middle'} className={'axis-label'} key={i}>
      {s}
    </text>
  ));
  return <g className={'axis-label'}>{text}</g>;
};
