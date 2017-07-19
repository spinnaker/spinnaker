import * as React from 'react';

import OpenDeleteModalButton from './openDeleteModalButton';

/*
 * Layout for canary config action buttons.
 */
export default function ConfigDetailActionButtons() {
  return (
    <div className="text-right">
      <OpenDeleteModalButton/>
    </div>
  );
}
