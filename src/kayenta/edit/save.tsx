import * as React from 'react';
import * as classNames from 'classnames';

import SaveConfigButton from './saveConfigButton';
import SaveConfigError from './saveConfigError';

export enum SaveConfigState {
  Saving,
  Saved,
  Error
}

/*
 * Responsible for canary config save component layout.
 */
export default function Save() {
  return (
    <div className={classNames('col-sm-12', 'text-right')}>
      <SaveConfigError/>
      <SaveConfigButton/>
    </div>
  );
}
