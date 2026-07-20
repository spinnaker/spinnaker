import React from 'react';

import { CiBuildExecutionDetails } from '../jenkins/CiBuildExecutionDetails';

export const TravisExecutionDetails: React.SFC<any> & { title: string } = (props) => {
  return <CiBuildExecutionDetails {...props} title="Travis Stage Configuration" buildServiceLabel="Build Service" />;
};

TravisExecutionDetails.title = 'travisConfig';
