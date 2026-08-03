import { UrlBuilder } from '../../navigation';
import { getProjectClusterViewModel } from './ProjectClusterModel';

describe('ProjectClusterModel', () => {
  const project = { name: 'kubernetesproject' } as any;

  beforeEach(() => {
    spyOn(UrlBuilder, 'buildFromMetadata').and.callFake((metadata: any) => {
      return [
        '#/clusters',
        metadata.project,
        metadata.application,
        metadata.account,
        metadata.region || 'all',
        metadata.stack || 'no-stack',
        metadata.detail || 'no-detail',
        metadata.cluster || 'no-cluster',
      ].join('/');
    });
  });

  it('derives sorted regions and maps application clusters by region', () => {
    const cluster = {
      account: 'k8s-local',
      stack: '*',
      detail: '*',
      instanceCounts: { total: 16, up: 16 },
      applications: [
        {
          application: 'kubernetesapp',
          clusters: [
            { region: 'prod', instanceCounts: { total: 8, up: 8 } },
            { region: 'dev', instanceCounts: { total: 8, up: 8 } },
          ],
        },
      ],
    };

    const viewModel = getProjectClusterViewModel(project, cluster as any, {});

    expect(viewModel.clusterLabel).toBe('*-*');
    expect(viewModel.regions).toEqual(['dev', 'prod']);
    expect(viewModel.instanceCounts).toEqual({ total: 16, up: 16 });
    expect(viewModel.applications[0].regions.dev.region).toBe('dev');
    expect(viewModel.applications[0].regions.prod.region).toBe('prod');
  });

  it('filters regions and sums visible instance counts', () => {
    const cluster = {
      account: 'k8s-local',
      stack: '*',
      detail: '*',
      instanceCounts: { total: 24, up: 24, down: 0 },
      applications: [
        {
          application: 'kubernetesapp',
          clusters: [
            { region: 'dev', instanceCounts: { total: 8, up: 8, down: 0 } },
            { region: 'prod', instanceCounts: { total: 8, up: 8, down: 0 } },
            { region: 'test', instanceCounts: { total: 8, up: 8, down: 0 } },
          ],
        },
      ],
    };

    const viewModel = getProjectClusterViewModel(project, cluster as any, { dev: true, prod: true });

    expect(viewModel.regions).toEqual(['dev', 'prod']);
    expect(viewModel.instanceCounts).toEqual({ total: 16, up: 16, down: 0 });
  });

  it('selects highest application build and flags inconsistent region builds', () => {
    const cluster = {
      account: 'prod',
      stack: '',
      detail: '',
      instanceCounts: { total: 2, up: 2 },
      applications: [
        {
          application: 'app1',
          clusters: [
            {
              region: 'us-east-1',
              instanceCounts: { total: 1, up: 1 },
              builds: [{ host: 'https://ci.example/', job: 'deploy', buildNumber: '1' }],
            },
            {
              region: 'us-west-1',
              instanceCounts: { total: 1, up: 1 },
              builds: [
                { host: 'https://ci.example/', job: 'deploy', buildNumber: '1' },
                { host: 'https://ci.example/', job: 'deploy', buildNumber: '3' },
              ],
            },
          ],
        },
      ],
    };

    const viewModel = getProjectClusterViewModel(project, cluster as any, {});
    const application = viewModel.applications[0];

    expect(application.build.buildNumber).toBe('3');
    expect(application.build.url).toBe('https://ci.example/job/deploy/3/');
    expect(application.hasInconsistentBuilds).toBe(true);
    expect(application.regions['us-east-1'].inconsistentBuilds).toEqual([
      { host: 'https://ci.example/', job: 'deploy', buildNumber: '1' },
    ]);
  });

  it('builds application and region cluster href metadata', () => {
    const cluster = {
      account: 'k8s-local',
      stack: '*',
      detail: '*',
      instanceCounts: { total: 8, up: 8 },
      applications: [
        {
          application: 'kubernetesapp',
          clusters: [{ region: 'dev', instanceCounts: { total: 8, up: 8 } }],
        },
      ],
    };

    const viewModel = getProjectClusterViewModel(project, cluster as any, {});
    const application = viewModel.applications[0];

    expect(application.metadata.href).toBe(
      '#/clusters/kubernetesproject/kubernetesapp/k8s-local/all/no-stack/no-detail/no-cluster',
    );
    expect(application.regions.dev.metadata.href).toBe(
      '#/clusters/kubernetesproject/kubernetesapp/k8s-local/dev/no-stack/no-detail/no-cluster',
    );
    expect(UrlBuilder.buildFromMetadata).toHaveBeenCalledWith(
      jasmine.objectContaining({
        type: 'clusters',
        project: 'kubernetesproject',
        application: 'kubernetesapp',
        account: 'k8s-local',
        region: 'dev',
      }),
    );
  });
});
