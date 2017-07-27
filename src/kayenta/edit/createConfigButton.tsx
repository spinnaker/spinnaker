import * as React from 'react';
import { UISref } from '@uirouter/react';

/*
* Button for creating a new canary config.
*/
export default function CreateConfigButton() {
  return (
    <UISref to=".configDetail" params={{configName: null, isNew: true}}>
      <button>New</button>
    </UISref>
  );
}
