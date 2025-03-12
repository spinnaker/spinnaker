// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import React from 'react';

import { FormikFormField, TextInput } from '@spinnaker/core';

export function ExecutionRoleForm() {
  return (
    <FormikFormField
      name="role"
      label="Role ARN"
      input={(props) => <TextInput {...props} placeholder="Enter role ARN" name="role" />}
    />
  );
}
