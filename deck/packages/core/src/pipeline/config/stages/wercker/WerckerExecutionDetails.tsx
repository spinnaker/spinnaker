import React from 'react';

import { CiBuildExecutionDetails } from '../jenkins/CiBuildExecutionDetails';

export const WerckerExecutionDetails: React.SFC<any> & { title: string } = (props) => {
  return <CiBuildExecutionDetails {...props} title="Wercker Stage Configuration" buildServiceLabel="Build Service" />;
};

WerckerExecutionDetails.title = 'werckerConfig';
