import * as React from 'react';

import { AsgActionExecutionDetailsSection, IExecutionDetailsSectionProps } from '../core';

export function DisableAsgExecutionDetails(props: IExecutionDetailsSectionProps) {
  return <AsgActionExecutionDetailsSection {...props} action="Disabled" />;
}

export namespace DisableAsgExecutionDetails {
  export const title = 'disableServerGroupConfig';
}
