import React from 'react';

import { BuildServiceType } from '../../../../ci/igor.service';
import { CiBuildStageConfig } from '../jenkins/CiBuildStageConfig';

export function WerckerStageConfig(props: any) {
  return (
    <CiBuildStageConfig
      {...props}
      buildServiceType={BuildServiceType.Wercker}
      buildServiceLabel="Build Service"
      buildServicePlaceholder="Select a Wercker build service..."
      waitForCompletionHelpKey="wercker.waitForCompletion"
      markUnstableHelpKeyPrefix="pipeline.config.wercker.markUnstableAsSuccessful"
      showInlineParameters={true}
      werckerMode={true}
    />
  );
}
