import React from 'react';

import { BuildServiceType } from '../../../../ci/igor.service';
import { CiBuildStageConfig } from '../jenkins/CiBuildStageConfig';

export function TravisStageConfig(props: any) {
  return (
    <CiBuildStageConfig
      {...props}
      buildServiceType={BuildServiceType.Travis}
      buildServiceLabel="Build Service"
      buildServicePlaceholder="Select a build service..."
      jobPlaceholder="Select a job..."
      parametersHelpKey="pipeline.config.travis.parameters"
      waitForCompletionHelpKey="travis.waitForCompletion"
      markUnstableHelpKeyPrefix="pipeline.config.travis.markUnstableAsSuccessful"
      showInlineParameters={true}
      info={
        <div className="well alert alert-info" role="alert">
          <p>
            You can read properties from Travis builds by logging them in the build log using one of the following
            formats:
          </p>
          <pre>{`echo SPINNAKER_PROPERTY_key1=value1
echo SPINNAKER_PROPERTY_key2=value2
echo SPINNAKER_CONFIG_JSON={"key3": "value3", "key4": "value4"}`}</pre>
          The properties will be available in the stage context under the key <code>propertyFileContents</code>.
        </div>
      }
    />
  );
}
