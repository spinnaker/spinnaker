import { AccountService } from '@spinnaker/core';

import { KubernetesManifestCommandBuilder } from './manifestCommandBuilder.service';

describe('KubernetesManifestCommandBuilder', () => {
  beforeEach(() => {
    vi.spyOn(AccountService, 'getArtifactAccounts').mockReturnValue(
      Promise.resolve([{ name: 'artifact-account' }]) as any,
    );
  });

  it('uses the source account when it is available', async () => {
    vi.spyOn(AccountService, 'getAllAccountDetailsForProvider').mockReturnValue(
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
    vi.spyOn(AccountService, 'getAllAccountDetailsForProvider').mockReturnValue(
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
