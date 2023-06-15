// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import { isNil, isString } from 'lodash';

function isEmptyString(val: any) {
  if (isString(val)) {
    if (isNil(val) || val === '') {
      return true;
    }
  }
  return false;
}

export function upsertDefaults(initialValues: any, defaultValues: any) {
  Object.entries(defaultValues).forEach(([key, value]) => {
    if (!initialValues[key] && !isEmptyString(value)) {
      initialValues[key] = value;
    }
  });
  return initialValues;
}
