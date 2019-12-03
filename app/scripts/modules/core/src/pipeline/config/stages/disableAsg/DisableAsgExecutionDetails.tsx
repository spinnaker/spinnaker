import React from 'react';

import { AsgActionExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';

export function DisableAsgExecutionDetails(props: IExecutionDetailsSectionProps) {
  return <AsgActionExecutionDetailsSection {...props} action="Disabled" />;
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace DisableAsgExecutionDetails {
  export const title = 'disableServerGroupConfig';
}
