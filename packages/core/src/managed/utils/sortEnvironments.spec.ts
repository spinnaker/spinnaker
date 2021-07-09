import { IManagedApplicationEnvironmentSummary, IManagedArtifactVersionEnvironment } from '../../domain';
import { sortEnvironments } from './sortEnvironments';

type Env = 'test' | 'staging' | 'main';

const getBaseResponse = () => {
  const versionEnviroments: { [key in Env]: IManagedArtifactVersionEnvironment } = {
    test: {
      name: 'test',
      state: 'current',
      deployedAt: '2021-04-02T18:13:38.202Z',
      compareLink:
        'https://stash.corp.netflix.com/projects/spkr/repos/keel-nflx/compare/commits?targetBranch=2a6f62b40aa24a26d9738d4f6109726cbf284982&sourceBranch=7834ab670dabbd8a0d891ef6b761b7002181164b',
    },
    main: {
      name: 'main',
      state: 'current',
      deployedAt: '2021-04-02T18:59:53.108Z',
      constraints: [
        {
          type: 'manual-judgement',
          status: 'OVERRIDE_PASS',
        },
        {
          type: 'depends-on',
          status: 'PASS',
          attributes: { dependsOnEnvironment: 'staging' },
        },
      ],
      compareLink:
        'https://stash.corp.netflix.com/projects/spkr/repos/keel-nflx/compare/commits?targetBranch=998424c738bf8f2c00586ac5c2d3a4205e1cf80f&sourceBranch=7834ab670dabbd8a0d891ef6b761b7002181164b',
    },
    staging: {
      name: 'staging',
      state: 'current',
      deployedAt: '2021-04-02T18:32:03.438Z',
      constraints: [
        {
          type: 'depends-on',
          status: 'PASS',
          attributes: { dependsOnEnvironment: 'test' },
        },
      ],
      compareLink:
        'https://stash.corp.netflix.com/projects/spkr/repos/keel-nflx/compare/commits?targetBranch=2a6f62b40aa24a26d9738d4f6109726cbf284982&sourceBranch=7834ab670dabbd8a0d891ef6b761b7002181164b',
    },
  };

  const response: IManagedApplicationEnvironmentSummary = {
    applicationPaused: false,
    hasManagedResources: true,
    resources: [],
    environments: Object.values(versionEnviroments).map((env) => ({ name: env.name, artifacts: [], resources: [] })),
    artifacts: [
      {
        name: 'keel',
        type: 'deb',
        reference: 'keel',
        versions: [
          {
            version: 'v2',
            displayName: '0.1006.0',
            environments: Object.values(versionEnviroments),
            createdAt: '2021-04-02T17:57:58.601Z',
            lifecycleSteps: [],
          },
          {
            version: 'v1',
            displayName: '0.1006.0',
            environments: [versionEnviroments.test, versionEnviroments.staging, versionEnviroments.main],
            createdAt: '2021-04-02T17:57:58.601Z',
            lifecycleSteps: [],
          },
        ],
      },
    ],
  };
  return { response, versionEnviroments };
};

describe('sort environments', () => {
  it('verify correct order in artifacts', () => {
    const { response } = getBaseResponse();
    sortEnvironments(response);
    for (const version of response.artifacts[0].versions) {
      const versionEnvs = version.environments;
      expect(versionEnvs[0].name).toBe('main');
      expect(versionEnvs[1].name).toBe('staging');
      expect(versionEnvs[2].name).toBe('test');
    }
  });

  it('verify environments in the root are in the correct order', () => {
    const { response } = getBaseResponse();
    sortEnvironments(response);
    expect(response.environments[0].name).toBe('main');
    expect(response.environments[1].name).toBe('staging');
    expect(response.environments[2].name).toBe('test');
  });

  it('keep order if no constraints found', () => {
    const { response, versionEnviroments } = getBaseResponse();
    Object.values(versionEnviroments).forEach((env) => (env.constraints = []));
    sortEnvironments(response);
    const versionEnvs = response.artifacts[0].versions[1].environments;
    expect(versionEnvs[0].name).toBe('test');
    expect(versionEnvs[1].name).toBe('staging');
    expect(versionEnvs[2].name).toBe('main');
  });

  it('move the env with a constraint to the beginning', () => {
    const { response, versionEnviroments } = getBaseResponse();
    Object.values(versionEnviroments).forEach((env) => {
      if (env.name === 'main') {
        env.constraints = [];
      }
    });
    sortEnvironments(response);
    for (const version of response.artifacts[0].versions) {
      expect(version.environments[0].name).toBe('staging');
    }
    expect(response.environments[0].name).toBe('staging');
  });
});
