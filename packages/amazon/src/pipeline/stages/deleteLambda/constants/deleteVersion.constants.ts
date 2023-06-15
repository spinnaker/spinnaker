// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

export interface IDeleteVersionConstant {
  description: string;
  label: string;
  value: string;
}

export const DeleteVersionList: IDeleteVersionConstant[] = [
  {
    label: 'Newest Function Version',
    value: '$LATEST',
    description: 'Delete the most recently deployed function version when this stage starts.',
  },
  {
    label: 'Previous Function Version',
    value: '$PREVIOUS',
    description: 'Delete the second-most recently deployed function version when this stage starts.',
  },
  {
    label: 'Older Than N',
    value: '$MOVING',
    description: 'Delete all version but the N most recent versions.',
  },
  {
    label: 'Provide Version Number',
    value: '$PROVIDED',
    description: 'Provide a specific version number to delete.',
  },
  {
    label: 'All Function Versions',
    value: '$ALL',
    description:
      'Delete all function versions and function infrastructure. This will completely delete the Lambda function.',
  },
];
