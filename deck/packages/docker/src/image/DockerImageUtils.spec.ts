import { DockerImageUtils } from './DockerImageUtils';

describe('imageId parsing', () => {
  it('parses undefined without choking', () => {
    expect(DockerImageUtils.splitImageId(undefined)).toEqual({
      organization: '',
      repository: '',
      digest: undefined,
      tag: undefined,
    });
  });

  it('parses image with no organization and no tag/digest', () => {
    const imageId = 'image';
    expect(DockerImageUtils.splitImageId(imageId)).toEqual({
      organization: '',
      repository: 'image',
      digest: undefined,
      tag: undefined,
    });
  });

  it('parses image with tag but no organization', () => {
    const imageId = 'image:tag';
    expect(DockerImageUtils.splitImageId(imageId)).toEqual({
      organization: '',
      repository: 'image',
      digest: undefined,
      tag: 'tag',
    });
  });

  it('parses image with no organization and correctly distinguishes digest from tag', () => {
    const imageId = 'image:sha256:abc123';
    expect(DockerImageUtils.splitImageId(imageId)).toEqual({
      organization: '',
      repository: 'image',
      digest: 'sha256:abc123',
      tag: undefined,
    });
  });

  it('parses image with organization but no tag/digest', () => {
    const imageId = 'organization/image';
    expect(DockerImageUtils.splitImageId(imageId)).toEqual({
      organization: 'organization',
      repository: 'organization/image',
      digest: undefined,
      tag: undefined,
    });
  });

  it('parses image with organization and tag', () => {
    const imageId = 'organization/image:tag';
    expect(DockerImageUtils.splitImageId(imageId)).toEqual({
      organization: 'organization',
      repository: 'organization/image',
      digest: undefined,
      tag: 'tag',
    });
  });

  it('parses image with organization and correctly distinguishes digest from tag', () => {
    const imageId = 'organization/image@sha256:abc123';
    expect(DockerImageUtils.splitImageId(imageId)).toEqual({
      organization: 'organization',
      repository: 'organization/image',
      digest: 'sha256:abc123',
      tag: undefined,
    });
  });

  it('parses image with nested organization', () => {
    const imageId = 'nested/organization/image';
    expect(DockerImageUtils.splitImageId(imageId)).toEqual({
      organization: 'nested/organization',
      repository: 'nested/organization/image',
      digest: undefined,
      tag: undefined,
    });
  });

  it('parses image with tag and nested organization', () => {
    const imageId = 'nested/organization/image:tag';
    expect(DockerImageUtils.splitImageId(imageId)).toEqual({
      organization: 'nested/organization',
      repository: 'nested/organization/image',
      digest: undefined,
      tag: 'tag',
    });
  });

  it('parses image with nested organization and correctly distinguishes digest from tag', () => {
    const imageId = 'nested/organization/image@sha256:abc123';
    expect(DockerImageUtils.splitImageId(imageId)).toEqual({
      organization: 'nested/organization',
      repository: 'nested/organization/image',
      digest: 'sha256:abc123',
      tag: undefined,
    });
  });
});

describe('imageId generating', () => {
  it('generate imageId without repository', () => {
    expect(
      DockerImageUtils.generateImageId({
        organization: 'gcr.io/project',
        repository: '',
        digest: undefined,
        tag: undefined,
      }),
    ).toEqual(undefined);
  });

  it('generate imageId with repository but no tag/digest', () => {
    expect(
      DockerImageUtils.generateImageId({
        organization: 'gcr.io/project',
        repository: 'gcr.io/project/my-image',
        digest: undefined,
        tag: undefined,
      }),
    ).toEqual(undefined);
  });

  it('generate imageId with digest', () => {
    expect(
      DockerImageUtils.generateImageId({
        organization: 'gcr.io/project',
        repository: 'gcr.io/project/my-image',
        digest: 'sha256:28f82eba',
        tag: undefined,
      }),
    ).toEqual('gcr.io/project/my-image@sha256:28f82eba');
  });

  it('generate imageId with tag', () => {
    expect(
      DockerImageUtils.generateImageId({
        organization: 'gcr.io/project',
        repository: 'gcr.io/project/my-image',
        digest: undefined,
        tag: 'v1.2',
      }),
    ).toEqual('gcr.io/project/my-image:v1.2');
  });
});
