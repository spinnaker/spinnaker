// Copyright 2026 spinnaker.io
// SPDX-License-Identifier: Apache-2.0

import {
  getDnsName,
  getKmsKeyArn,
  getOkActions,
  getSslCertificateId,
  getVpcId,
  getVpcZoneIdentifier,
} from './awsSdkFieldNames';

describe('awsSdkFieldNames', () => {
  const cases: Array<{ name: string; read: (o: any) => any; current: string; legacy: string }> = [
    {
      name: 'getVpcZoneIdentifier',
      read: getVpcZoneIdentifier,
      current: 'vpcZoneIdentifier',
      legacy: 'vpczoneIdentifier',
    },
    { name: 'getDnsName', read: getDnsName, current: 'dnsName', legacy: 'dnsname' },
    { name: 'getVpcId', read: getVpcId, current: 'vpcId', legacy: 'vpcid' },
    { name: 'getSslCertificateId', read: getSslCertificateId, current: 'sslCertificateId', legacy: 'sslcertificateId' },
    { name: 'getKmsKeyArn', read: getKmsKeyArn, current: 'kmsKeyArn', legacy: 'kmskeyArn' },
    { name: 'getOkActions', read: getOkActions, current: 'okActions', legacy: 'okactions' },
  ];

  cases.forEach(({ name, read, current, legacy }) => {
    describe(name, () => {
      it(`reads ${current}`, () => {
        expect(read({ [current]: 'new' })).toBe('new');
      });

      it(`falls back to legacy ${legacy}`, () => {
        expect(read({ [legacy]: 'old' })).toBe('old');
      });

      it(`prefers ${current} over ${legacy}`, () => {
        expect(read({ [current]: 'new', [legacy]: 'old' })).toBe('new');
      });

      it('tolerates a missing object', () => {
        expect(read(undefined)).toBeUndefined();
      });
    });
  });
});
