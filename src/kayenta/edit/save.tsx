import * as React from 'react';
import * as classNames from 'classnames';

import ValidationErrors from './validationErrors';
import SaveConfigButton from './saveConfigButton';
import SaveConfigError from './saveConfigError';

/*
 * Responsible for canary config save component layout.
 */
export default function Save() {
  return (
    <div className={classNames('col-sm-12', 'text-right')}>
      <ValidationErrors/>
      <SaveConfigError/>
      <SaveConfigButton/>
    </div>
  );
}
