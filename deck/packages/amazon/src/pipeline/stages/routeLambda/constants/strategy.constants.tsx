// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

export interface IStrategyConstant {
  description: string;
  label: string;
  value: string;
}

export const DeploymentStrategyList: IStrategyConstant[] = [
  {
    label: 'Simple',
    value: '$SIMPLE',
    description: 'Route 100% of traffic to specified version',
  },
  {
    label: 'Weighted Deployment',
    value: '$WEIGHTED',
    description: 'Split the traffic weight between two function versions.',
  },
  {
    label: 'Blue/Green',
    value: '$BLUEGREEN',
    description: 'Disable all previous versions once the latest version passes health checks.',
  },
];
