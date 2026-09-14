import type { IExecution, IPipeline, IProject } from '../../domain';
import { getProjectPipelineConfigIds, getProjectPipelineGroups } from './ProjectPipelineModel';

const project = {
  name: 'commerce',
  config: {
    applications: [],
    clusters: [],
    pipelineConfigs: [{ application: 'legacy', pipelineConfigId: 'manual' }],
  },
} as IProject;

const pipeline = (id: string, application: string, name: string, tags: any[] = []): IPipeline =>
  ({
    id,
    application,
    name,
    tags,
    stages: [],
    triggers: [],
    parameterConfig: [],
    limitConcurrent: true,
    keepWaitingPipelines: false,
  } as IPipeline);

const execution = (pipelineConfigId: string, application: string, name: string, startTime: number): IExecution =>
  ({
    id: `${pipelineConfigId}-${startTime}`,
    pipelineConfigId,
    application,
    name,
    startTime,
    stages: [],
    stageSummaries: [],
    trigger: {},
  } as IExecution);

describe('ProjectPipelineModel', () => {
  it('unions manual IDs with exact project tag matches', () => {
    const configs = [
      pipeline('tagged', 'payments', 'Deploy', [{ name: 'project', value: 'commerce' }]),
      pipeline('wrong-name', 'payments', 'Wrong name', [{ name: 'Project', value: 'commerce' }]),
      pipeline('wrong-value', 'payments', 'Wrong value', [{ name: 'project', value: 'Commerce' }]),
      pipeline('wrong-space', 'payments', 'Wrong space', [{ name: 'project', value: ' commerce' }]),
    ];

    expect(getProjectPipelineConfigIds(project, configs)).toEqual(['manual', 'tagged']);
  });

  it('deduplicates a pipeline that is manual and tagged', () => {
    const manualAndTagged = pipeline('manual', 'legacy', 'Deploy', [{ name: 'project', value: 'commerce' }]);

    expect(getProjectPipelineConfigIds(project, [manualAndTagged])).toEqual(['manual']);
  });

  it('allows multiple project tags and includes disabled pipeline configs', () => {
    const shared = {
      ...pipeline('shared', 'platform', 'Shared deploy', [
        { name: 'project', value: 'platform' },
        { name: 'project', value: 'commerce' },
      ]),
      disabled: true,
    };

    expect(getProjectPipelineConfigIds(project, [shared])).toEqual(['manual', 'shared']);
  });

  it('groups by application, sorts names, and keeps the newest execution', () => {
    const configs = [
      pipeline('zeta', 'payments', 'Zeta', [{ name: 'project', value: 'commerce' }]),
      pipeline('alpha', 'payments', 'Alpha', [{ name: 'project', value: 'commerce' }]),
      pipeline('catalog', 'catalog', 'Deploy', [{ name: 'project', value: 'commerce' }]),
    ];
    const executions = [
      execution('alpha', 'payments', 'Alpha', 10),
      execution('alpha', 'payments', 'Alpha', 20),
      execution('manual', 'legacy', 'Legacy deploy', 15),
    ];

    const groups = getProjectPipelineGroups(project, configs, executions);

    expect(groups.map((group) => group.application)).toEqual(['catalog', 'legacy', 'payments']);
    expect(groups[2].pipelines.map((row) => row.name)).toEqual(['Alpha', 'Zeta']);
    expect(groups[2].pipelines[0].execution.startTime).toBe(20);
    expect(groups[2].pipelines[1].execution).toBeUndefined();
    expect(groups[1].pipelines[0].name).toBe('Legacy deploy');
  });
});
