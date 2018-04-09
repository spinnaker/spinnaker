import * as React from 'react';

import { AsgActionExecutionDetailsSection, IExecutionDetailsSectionProps } from '../core';

export function DestroyAsgExecutionDetails(props: IExecutionDetailsSectionProps) {
  return <AsgActionExecutionDetailsSection {...props} action="Destroyed" />;
}

export namespace DestroyAsgExecutionDetails {
  export const title = 'destroyServerGroupConfig';
}
