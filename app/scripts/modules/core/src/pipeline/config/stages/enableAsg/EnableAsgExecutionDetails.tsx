import * as React from 'react';

import { AsgActionExecutionDetailsSection, IExecutionDetailsSectionProps } from '../core';

export function EnableAsgExecutionDetails(props: IExecutionDetailsSectionProps) {
  return <AsgActionExecutionDetailsSection {...props} action="Enabled" />;
}

export namespace EnableAsgExecutionDetails {
  export const title = 'enableServerGroupConfig';
}
