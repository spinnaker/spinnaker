import { UIView } from '@uirouter/react';
import * as React from 'react';

export default () => {
  const noWrap = { wrap: false };
  return (
    <div className="vertical flex-1">
      <UIView {...noWrap} name="detail" />
    </div>
  );
};
