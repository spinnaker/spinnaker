// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import React from 'react';

import type { IFormikStageConfigInjectedProps } from '@spinnaker/core';

import { InvokeLambdaHealthCheck } from './InvocationHealthCheck';

export function retrieveHealthCheck(value: string, props: IFormikStageConfigInjectedProps) {
  switch (value) {
    case '$LAMBDA':
      return <InvokeLambdaHealthCheck {...props} />;
    case '$WEIGHTED':
      return null;
    case '$BLUEGREEN':
      return null;
    default:
      return null;
  }
}
