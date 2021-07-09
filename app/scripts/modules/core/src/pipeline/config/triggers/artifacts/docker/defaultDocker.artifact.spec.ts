import { IArtifact } from '../../../../../domain';

import { setNameAndVersionFromReference } from './DockerArtifactEditor';

describe('defaultDocker.artifact', () => {
  it('parses Docker image references correctly', () => {
    const correctArtifacts: IArtifact[] = [
      {
        id: '1',
        reference: 'nginx:112',
        name: 'nginx',
        version: '112',
      },
      {
        id: '2',
        reference: 'nginx:1.12-alpine',
        name: 'nginx',
        version: '1.12-alpine',
      },
      {
        id: '3',
        reference: 'my-nginx:100000',
        name: 'my-nginx',
        version: '100000',
      },
      {
        id: '4',
        reference: 'my.nginx:100000',
        name: 'my.nginx',
        version: '100000',
      },
      {
        id: '5',
        reference: 'reg/repo:1.2.3',
        name: 'reg/repo',
        version: '1.2.3',
      },
      {
        id: '6',
        reference: 'reg.repo:123@sha256:13',
        name: 'reg.repo:123',
        version: 'sha256:13',
      },
      {
        id: '7',
        reference: 'reg.default.svc/r/j:485fabc',
        name: 'reg.default.svc/r/j',
        version: '485fabc',
      },
      {
        id: '8',
        reference: 'reg:5000/r/j:485fabc',
        name: 'reg:5000/r/j',
        version: '485fabc',
      },
      {
        id: '9',
        reference: 'reg:5000/r__j:485fabc',
        name: 'reg:5000/r__j',
        version: '485fabc',
      },
      {
        id: '10',
        reference: 'clouddriver',
        name: 'clouddriver',
      },
      {
        id: '11',
        reference: 'clouddriver@sha256:9145',
        name: 'clouddriver',
        version: 'sha256:9145',
      },
      {
        id: '12',
        reference: 'localhost:5000/test/busybox@sha256:cbbf22',
        name: 'localhost:5000/test/busybox',
        version: 'sha256:cbbf22',
      },
    ];

    correctArtifacts.forEach((correctArtifact) => {
      const artifact: IArtifact = {
        id: '123',
        reference: correctArtifact.reference,
      };
      setNameAndVersionFromReference(artifact);
      expect(artifact.name).toBe(correctArtifact.name);
      expect(artifact.version).toBe(correctArtifact.version);
    });
  });
});
