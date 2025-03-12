// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

export interface IHealthConstant {
  label: string;
  value: string;
}

export const HealthCheckList: IHealthConstant[] = [
  {
    label: 'Lambda Invocation',
    value: '$LAMBDA',
  },
];
