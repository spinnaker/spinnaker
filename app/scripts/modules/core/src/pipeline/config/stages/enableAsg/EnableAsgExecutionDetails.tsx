import React from 'react';

import { AsgActionExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';

export function EnableAsgExecutionDetails(props: IExecutionDetailsSectionProps) {
  return <AsgActionExecutionDetailsSection {...props} action="Enabled" />;
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace EnableAsgExecutionDetails {
  export const title = 'enableServerGroupConfig';
}
