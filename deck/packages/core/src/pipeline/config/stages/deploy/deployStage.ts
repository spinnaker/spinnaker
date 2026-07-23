import { DeployChangesExecutionDetails, DeployExecutionDetails } from './DeployExecutionDetails';
import { DeployStageConfig } from './DeployStageConfig';
import { ExecutionArtifactTab } from '../../../../artifact/react/ExecutionArtifactTab';
import { CloudProviderRegistry } from '../../../../cloudProvider/CloudProviderRegistry';
import type { ClusterService } from '../../../../cluster/cluster.service';
import { ExecutionDetailsTasks } from '../common';
import { deployStageTransformer } from './deployStage.transformer';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

import './deployStage.less';

Registry.pipeline.registerTransformer(deployStageTransformer);

export function registerDeployStage(clusterService: ClusterService): IStageTypeConfig {
  const stageConfig = {
    label: 'Deploy',
    description: 'Deploys the previously baked or found image',
    strategyDescription: 'Deploys the image specified',
    key: 'deploy',
    alias: 'createServerGroup',
    component: DeployStageConfig,
    executionDetailsSections: [
      DeployExecutionDetails,
      ExecutionDetailsTasks,
      ExecutionArtifactTab,
      DeployChangesExecutionDetails,
    ],
    supportsCustomTimeout: true,
    validators: [
      {
        type: 'stageBeforeType',
        stageTypes: ['bake', 'findAmi', 'findImage', 'findImageFromTags'],
        message: 'You must have a Bake or Find Image stage before any deploy stage.',
        skipValidation: (_pipeline: any, stage: any) =>
          (stage.clusters || []).every(
            (cluster: any) =>
              CloudProviderRegistry.getValue(cluster.provider, 'serverGroup.skipUpstreamStageCheck') ||
              clusterService.isDeployingArtifact(cluster),
          ),
      },
      {
        type: 'imageProviderBeforeType',
        message: 'You must provide the image metadata before any deploy stage.',
        triggerTypes: ['docker', 'jenkins'],
        skipValidation: (_pipeline: any, stage: any) =>
          (stage.clusters || []).every(
            (cluster: any) => !CloudProviderRegistry.getValue(cluster.provider, 'serverGroup.checkForImageProviders'),
          ),
      } as any,
    ],
    accountExtractor: (stage: any) => (stage.context.clusters || []).map((cluster: any) => cluster.account),
    configAccountExtractor: (stage: any) => (stage.clusters || []).map((cluster: any) => cluster.account),
    artifactExtractor: (stageContext: any) => {
      const clusters = stageContext.clusters || [stageContext];
      return clusters
        .map(clusterService.extractArtifacts, clusterService)
        .reduce((array: any[], items: any[]) => array.concat(items), []);
    },
    artifactRemover: (stage: any, artifactId: string) => {
      (stage.clusters || []).forEach((cluster: any) =>
        clusterService.getArtifactExtractor(cluster.cloudProvider).removeArtifact(cluster, artifactId),
      );
    },
    strategy: true,
  } as IStageTypeConfig;
  Registry.pipeline.registerStage(stageConfig);
  return stageConfig;
}
