// Copyright 2026 spinnaker.io
// SPDX-License-Identifier: Apache-2.0

import { AwsFunctionTransformer } from './function.transformer';

describe('AwsFunctionTransformer.convertFunctionForEditing', () => {
  const kmsKeyArn = 'arn:aws:kms:us-east-1:123456789012:key/abcd';

  [
    { desc: 'kmsKeyArn', functionDef: { kmsKeyArn } },
    { desc: 'legacy kmskeyArn', functionDef: { kmskeyArn: kmsKeyArn } },
  ].forEach(({ desc, functionDef }) => {
    it(`maps ${desc} onto the kmskeyArn upsert field`, () => {
      const command = new AwsFunctionTransformer().convertFunctionForEditing({
        account: 'test',
        region: 'us-east-1',
        ...functionDef,
      } as any);

      expect(command.kmskeyArn).toBe(kmsKeyArn);
    });
  });
});
