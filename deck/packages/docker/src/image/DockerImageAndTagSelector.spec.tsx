import { DockerChartAndTagSelector } from './DockerChartAndTagSelector';
import { DockerImageAndTagSelector } from './DockerImageAndTagSelector';

describe('Docker image selectors', () => {
  const props = {
    specifyTagByRegex: false,
    imageId: '',
    organization: '',
    registry: '',
    repository: '',
    tag: '',
    digest: '',
    account: '',
    onChange: () => undefined,
  };

  it('ignores image records without repositories when grouping account organizations', () => {
    const images = [
      { account: 'test', registry: 'registry.example', repository: undefined },
      { account: 'test', registry: 'registry.example', repository: 'spinnaker/deck', tag: 'latest' },
    ];

    expect((new DockerImageAndTagSelector(props) as any).getAccountMap(images)).toEqual({ test: ['spinnaker'] });
    expect((new DockerChartAndTagSelector(props) as any).getAccountMap(images)).toEqual({ test: ['spinnaker'] });
  });

  it('ignores non-Docker image records when no repositories are present', () => {
    const images = [
      { account: 'gce', imageName: 'ubuntu-1804-bionic-v20181003' },
      { account: 'compute-engine', imageName: 'ubuntu-1804-bionic-v20181003' },
    ];

    expect((new DockerImageAndTagSelector(props) as any).getAccountMap(images)).toEqual({});
    expect((new DockerChartAndTagSelector(props) as any).getAccountMap(images)).toEqual({});
  });
});
