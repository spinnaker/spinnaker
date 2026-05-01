// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
// https://docs.aws.amazon.com/lambda/latest/dg/lambda-runtimes.html
export const availableRuntimes = [
  'nodejs24.x',
  'nodejs22.x',
  'java25',
  'java21',
  'java17',
  'java11',
  'java8.al2',
  'python3.14',
  'python3.13',
  'python3.12',
  'python3.11',
  'python3.10',
  'dotnet10',
  'dotnet9',
  'dotnet8',
  'ruby4.0',
  'ruby3.4',
  'ruby3.3',
  'provided.al2023',
  'provided.al2',
];

export const lambdaHelpFields = {
  stack:
    '(Optional) Stack is naming components of a function, used to create vertical stacks of dependent services for integration testing.',
  detail:
    '(Optional) Detail is a string of free-form alphanumeric characters to describe any other variables in naming a function.',
};
