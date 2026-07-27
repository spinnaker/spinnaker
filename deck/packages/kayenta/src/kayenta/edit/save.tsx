import classNames from 'classnames';
import * as React from 'react';

import SaveConfigButton from './saveConfigButton';
import SaveConfigError from './saveConfigError';
import ValidationErrors from './validationErrors';

/*
 * Responsible for canary config save component layout.
 */
export default function Save() {
  return (
    <div className={classNames('col-sm-12', 'text-right')}>
      <ValidationErrors />
      <SaveConfigError />
      <SaveConfigButton />
    </div>
  );
}
