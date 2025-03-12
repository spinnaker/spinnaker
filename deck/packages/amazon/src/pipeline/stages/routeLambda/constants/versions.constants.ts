// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

export interface IVersionConstant {
  description: string;
  label: string;
  value: string;
}

export const VersionList: IVersionConstant[] = [
  {
    label: 'Newest Function Version',
    value: '$LATEST',
    description: 'Selects the most recently deployed function when this stage starts.',
  },
  {
    label: 'Previous Function Version',
    value: '$PREVIOUS',
    description: 'Selects the second-most recently deployed function when this stage starts.',
  },
  {
    label: 'Oldest Function Verion',
    value: '$OLDEST',
    description: 'Selects the least recently deployed function when this stage starts.',
  },
  {
    label: 'Provide Version Number',
    value: '$PROVIDED',
    description: 'Provide a specific version number to destroy.',
  },
];
