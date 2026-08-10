import { AccountService } from '@spinnaker/core';

import { KubernetesManifestCommandBuilder } from './manifestCommandBuilder.service';

describe('KubernetesManifestCommandBuilder', () => {
  beforeEach(() => {
    spyOn(AccountService, 'getArtifactAccounts').and.returnValue(
      Promise.resolve([{ name: 'artifact-account' }]) as any,
    );
  });

  it('uses the source account when it is available', async () => {
    spyOn(AccountService, 'getAllAccountDetailsForProvider').and.returnValue(
      Promise.resolve([{ name: 'fallback-account' }, { name: 'source-account' }]) as any,
    );

    const result = await KubernetesManifestCommandBuilder.buildNewManifestCommand(
      { name: 'frontend' } as any,
      { kind: 'Deployment' },
      undefined,
      'source-account',
    );

    expect(result.command.account).toBe('source-account');
  });

  it('falls back to the first account when the source account is unavailable', async () => {
    spyOn(AccountService, 'getAllAccountDetailsForProvider').and.returnValue(
      Promise.resolve([{ name: 'fallback-account' }]) as any,
    );

    const result = await KubernetesManifestCommandBuilder.buildNewManifestCommand(
      { name: 'frontend' } as any,
      { kind: 'Deployment' },
      undefined,
      'missing-account',
    );

    expect(result.command.account).toBe('fallback-account');
  });
});
