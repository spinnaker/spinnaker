// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
// https://docs.aws.amazon.com/lambda/latest/dg/lambda-runtimes.html
export const availableRuntimes = [
  'nodejs12.x',
  'nodejs14.x',
  'nodejs16.x',
  'nodejs18.x',
  'java8',
  'java8.al2',
  'java11',
  'java17',
  'python3.7',
  'python3.8',
  'python3.9',
  'python3.10',
  'dotnetcore3.1',
  'dotnet7',
  'dotnet6',
  'dotnet5.0',
  'go1.x',
  'ruby2.7',
  'provided',
  'provided.al2',
];

export const lambdaHelpFields = {
  stack:
    '(Optional) Stack is naming components of a function, used to create vertical stacks of dependent services for integration testing.',
  detail:
    '(Optional) Detail is a string of free-form alphanumeric characters to describe any other variables in naming a function.',
};
