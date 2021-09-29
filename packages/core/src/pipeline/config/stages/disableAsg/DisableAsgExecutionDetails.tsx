import React from 'react';

import type { IExecutionDetailsSectionProps } from '../common';
import { AsgActionExecutionDetailsSection } from '../common';

export function DisableAsgExecutionDetails(props: IExecutionDetailsSectionProps) {
  return <AsgActionExecutionDetailsSection {...props} action="Disabled" />;
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace DisableAsgExecutionDetails {
  export const title = 'disableServerGroupConfig';
}
