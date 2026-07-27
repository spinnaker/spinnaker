import * as React from 'react';

import CopyConfigButton from './copyConfigButton';
import OpenEditConfigJsonModalButton from './openConfigJsonModalButton';
import OpenDeleteModalButton from './openDeleteModalButton';

/*
 * Layout for canary config action buttons.
 */
export default function ConfigDetailActionButtons() {
  return (
    <ul className="list-inline pull-right">
      <li>
        <OpenEditConfigJsonModalButton />
      </li>
      <li>
        <CopyConfigButton />
      </li>
      <li>
        <OpenDeleteModalButton />
      </li>
    </ul>
  );
}
