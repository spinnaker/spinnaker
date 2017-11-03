import * as React from 'react';
import { UIView } from '@uirouter/react';

// Just a placeholder until it's clear what the
// main report page should look like.
export default () => {
  const noWrap = { wrap: false };
  return (
    <div>
      <UIView {...noWrap} name="detail"/>
    </div>
  );
};
