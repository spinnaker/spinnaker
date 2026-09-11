import type { IExecution, IPipeline, IProject } from '../../domain';

export interface IProjectPipelineRow {
  application: string;
  config?: IPipeline;
  execution?: IExecution;
  name: string;
  pipelineConfigId: string;
}

export interface IProjectPipelineGroup {
  application: string;
  pipelines: IProjectPipelineRow[];
}

type ProjectForPipelines = Pick<IProject, 'name' | 'config'>;

const hasProjectTag = (pipeline: IPipeline, projectName: string): boolean =>
  (pipeline.tags || []).some((tag) => tag.name === 'project' && tag.value === projectName);

export const getProjectPipelineConfigIds = (project: ProjectForPipelines, pipelineConfigs: IPipeline[]): string[] => {
  const ids = new Set((project.config.pipelineConfigs || []).map(({ pipelineConfigId }) => pipelineConfigId));
  pipelineConfigs
    .filter((pipeline) => hasProjectTag(pipeline, project.name))
    .forEach((pipeline) => ids.add(pipeline.id));
  return Array.from(ids);
};

export const getProjectPipelineGroups = (
  project: ProjectForPipelines,
  pipelineConfigs: IPipeline[],
  executions: IExecution[],
): IProjectPipelineGroup[] => {
  const selectedIds = getProjectPipelineConfigIds(project, pipelineConfigs);
  const selectedIdSet = new Set(selectedIds);
  const configsById = new Map(pipelineConfigs.map((config) => [config.id, config]));
  const manualById = new Map((project.config.pipelineConfigs || []).map((entry) => [entry.pipelineConfigId, entry]));
  const latestExecutionById = new Map<string, IExecution>();

  executions.forEach((candidate) => {
    if (!candidate.pipelineConfigId || !selectedIdSet.has(candidate.pipelineConfigId)) {
      return;
    }
    const current = latestExecutionById.get(candidate.pipelineConfigId);
    if (!current || (candidate.startTime || 0) > (current.startTime || 0)) {
      latestExecutionById.set(candidate.pipelineConfigId, candidate);
    }
  });

  const rows = selectedIds
    .map(
      (pipelineConfigId): IProjectPipelineRow => {
        const config = configsById.get(pipelineConfigId);
        const execution = latestExecutionById.get(pipelineConfigId);
        const application =
          config?.application || execution?.application || manualById.get(pipelineConfigId)?.application;
        const name = config?.name || execution?.name;
        return application && name ? { application, config, execution, name, pipelineConfigId } : null;
      },
    )
    .filter(Boolean) as IProjectPipelineRow[];

  const groupsByApplication = new Map<string, IProjectPipelineRow[]>();
  rows.forEach((row) => {
    const group = groupsByApplication.get(row.application) || [];
    group.push(row);
    groupsByApplication.set(row.application, group);
  });

  return Array.from(groupsByApplication.entries())
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([application, pipelines]) => ({
      application,
      pipelines: pipelines.sort((left, right) => left.name.localeCompare(right.name)),
    }));
};
