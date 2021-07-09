import { AppengineServerGroupTransformer } from './transformer';

describe('command transforms', () => {
  it('converts the command into an AppengineDeployDescription object', () => {
    const transformer = new AppengineServerGroupTransformer(null);
    const command: any = {
      expectedArtifact: {
        id: '1234',
        type: 'docker/image',
        name: 'gcr.io/hale-entry-305216/test',
        version: 'test',
        reference: 'gcr.io/hale-entry-305216/test:test',
        artifactAccount: 'docker-registry',
        customKind: true,
      },
      credentials: 'credentials',
      region: 'us-central',
      selectedProvider: 'appengine',
      applicationDirectoryRoot: '',
      viewState: {
        mode: '',
        submitButtonLabel: '',
        disableStrategySelection: true,
      },
      expectedArtifactId: '',
      interestingHealthProviderNames: [],
      backingData: '',
      fromArtifact: false,
      sourceType: '',
      configArtifacts: [],
      application: 'dockerImageApp',
    };

    let transformed = transformer.convertServerGroupCommandToDeployConfiguration(command);
    expect(transformed.cloudProvider).toBe('appengine');
    expect(transformed.provider).toBe('appengine');
    expect(transformed.application).toBe('dockerImageApp');
    expect(transformed.expectedArtifact.name).toBe('gcr.io/hale-entry-305216/test');
  });
});
