import { DockerTriggerTemplate } from './DockerTriggerTemplate';

describe('<DockerTriggerTemplate/>', () => {
  it('formats Docker trigger labels without Angular promises', async () => {
    await expectAsync(
      Promise.resolve(
        DockerTriggerTemplate.formatLabel({ account: 'prod-registry', repository: 'example/service' } as any),
      ),
    ).toBeResolvedTo('(Docker Registry) prod-registry: example/service');
  });

  it('writes docker image artifacts using tag references', () => {
    const updateCommand = jasmine.createSpy('updateCommand');
    const component = new DockerTriggerTemplate({
      command: {
        trigger: {
          type: 'docker',
          registry: 'registry.example.com',
          repository: 'example/service',
        },
      },
      updateCommand,
    } as any);

    (component as any).updateArtifact((component.props as any).command, '1.260101.000000-0000000');

    expect(updateCommand).toHaveBeenCalledWith('extraFields.tag', '1.260101.000000-0000000');
    expect(updateCommand).toHaveBeenCalledWith('extraFields.artifacts', [
      {
        type: 'docker/image',
        name: 'registry.example.com/example/service',
        version: '1.260101.000000-0000000',
        reference: 'registry.example.com/example/service:1.260101.000000-0000000',
      },
    ]);
  });

  it('writes Helm OCI image artifacts using digest references', () => {
    const updateCommand = jasmine.createSpy('updateCommand');
    const component = new DockerTriggerTemplate({
      command: {
        trigger: {
          type: 'helm/oci',
          registry: 'registry.example.com',
          repository: 'charts/service',
        },
      },
      updateCommand,
    } as any);
    (component as any).state.lookupType = 'digest';

    (component as any).updateArtifact((component.props as any).command, 'sha256:abc123');

    expect(updateCommand).toHaveBeenCalledWith('extraFields.tag', 'sha256:abc123');
    expect(updateCommand).toHaveBeenCalledWith('extraFields.artifacts', [
      {
        type: 'helm/image',
        name: 'registry.example.com/charts/service',
        version: 'sha256:abc123',
        reference: 'registry.example.com/charts/service@sha256:abc123',
      },
    ]);
  });
});
