import React from 'react';

import { CiBuildStageConfig } from './CiBuildStageConfig';
import { BuildServiceType } from '../../../../ci/igor.service';

export function JenkinsStageConfig(props: any) {
  return (
    <CiBuildStageConfig
      {...props}
      buildServiceType={BuildServiceType.Jenkins}
      buildServiceLabel="Controller"
      buildServicePlaceholder="Select a controller..."
      jobPlaceholder="Select a job..."
      propertyFileHelpKey="pipeline.config.jenkins.propertyFile"
      waitForCompletionHelpKey="jenkins.waitForCompletion"
      markUnstableHelpKeyPrefix="pipeline.config.jenkins.markUnstableAsSuccessful"
      showJenkinsParameters={true}
    />
  );
}
