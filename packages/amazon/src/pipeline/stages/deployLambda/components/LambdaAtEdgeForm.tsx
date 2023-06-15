// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import React from 'react';

import type { IFormikStageConfigInjectedProps } from '@spinnaker/core';
import { CheckboxInput, FormikFormField, HelpField } from '@spinnaker/core';

const helpFieldContent = {
  lambdaAtEdge:
    'Validate AWS Lambda function configuration against Lambda@Edge requirements. This will not enable Lambda@Edge on this function. ',
};
export function LambdaAtEdgeForm(props: IFormikStageConfigInjectedProps) {
  const { values } = props.formik;
  if (values.region !== 'us-east-1') {
    return <div className="horizontal center">Lambda@Edge is only available in region us-east-1.</div>;
  }
  return (
    <div>
      <FormikFormField
        name="enableLambdaAtEdge"
        label="Enable Lambda@Edge Validation"
        help={<HelpField content={helpFieldContent.lambdaAtEdge} />}
        input={(props) => <CheckboxInput {...props} />}
      />
    </div>
  );
}
