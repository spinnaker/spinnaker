import * as React from 'react';

import OpenDeleteModalButton from './openDeleteModalButton';
import OpenEditConfigJsonModalButton from './openEditConfigJsonModalButton'
import CopyConfigButton from './copyConfigButton';

/*
 * Layout for canary config action buttons.
 */
export default function ConfigDetailActionButtons() {
  return (
    <ul className="list-inline pull-right">
      <li><OpenEditConfigJsonModalButton/></li>
      <li><CopyConfigButton/></li>
      <li><OpenDeleteModalButton/></li>
    </ul>
  );
}
