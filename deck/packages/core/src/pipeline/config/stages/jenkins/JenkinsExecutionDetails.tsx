import React from 'react';

import { CiBuildExecutionDetails } from './CiBuildExecutionDetails';

export const JenkinsExecutionDetails: React.SFC<any> & { title: string } = (props) => {
  return <CiBuildExecutionDetails {...props} title="Jenkins Stage Configuration" buildServiceLabel="Controller" />;
};

JenkinsExecutionDetails.title = 'jenkinsConfig';
