import React from 'react';

import { AsgActionExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';

export function DestroyAsgExecutionDetails(props: IExecutionDetailsSectionProps) {
  return <AsgActionExecutionDetailsSection {...props} action="Destroyed" />;
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace DestroyAsgExecutionDetails {
  export const title = 'destroyServerGroupConfig';
}
