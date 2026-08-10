import { shallow } from 'enzyme';
import React from 'react';

import { DockerTriggerTemplate } from './DockerTriggerTemplate';
import { DockerImageReader } from '../../image';

interface IDeferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
}

function deferred<T>(): IDeferred<T> {
  let resolve: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => (resolve = promiseResolve));
  return { promise, resolve };
}

describe('<DockerTriggerTemplate/>', () => {
  it('formats Docker trigger labels', async () => {
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
  it('aborts superseded and unmounted tag queries without publishing cancellation errors', async () => {
    jasmine.clock().install();
    try {
      const firstRequest = deferred<string[]>();
      const secondRequest = deferred<string[]>();
      const findTags = spyOn(DockerImageReader, 'findTags').and.returnValues(
        firstRequest.promise,
        secondRequest.promise,
      );
      const wrapper = shallow(
        <DockerTriggerTemplate
          command={{
            trigger: { type: 'docker', repository: 'example/service' },
          }}
          updateCommand={jasmine.createSpy('updateCommand')}
        />,
        { disableLifecycleMethods: true },
      );
      const component = wrapper.instance() as DockerTriggerTemplate;
      const tagLoadSuccess = spyOn(component as any, 'tagLoadSuccess').and.callThrough();
      const tagLoadFailure = spyOn(component as any, 'tagLoadFailure').and.callThrough();

      (component as any).initialize();
      jasmine.clock().tick(250);
      (component as any).searchTags();
      jasmine.clock().tick(250);
      const firstSignal = findTags.calls.argsFor(0)[1] as AbortSignal;
      const secondSignal = findTags.calls.argsFor(1)[1] as AbortSignal;

      expect(firstSignal.aborted).toBe(true);
      expect(secondSignal.aborted).toBe(false);
      wrapper.unmount();
      expect(secondSignal.aborted).toBe(true);

      firstRequest.resolve(['stale']);
      secondRequest.resolve(['late']);
      await Promise.all([firstRequest.promise, secondRequest.promise]);
      await Promise.resolve();

      expect(tagLoadSuccess).not.toHaveBeenCalled();
      expect(tagLoadFailure).not.toHaveBeenCalled();
    } finally {
      jasmine.clock().uninstall();
    }
  });
});
