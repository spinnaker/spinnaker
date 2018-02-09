import * as React from 'react';
import { UIView } from '@uirouter/react';

export default () => {
  const noWrap = { wrap: false };
  return (
    <div className="vertical flex-1">
      <UIView {...noWrap} name="detail"/>
    </div>
  );
};
