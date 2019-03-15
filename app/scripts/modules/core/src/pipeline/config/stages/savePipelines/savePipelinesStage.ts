import { ArtifactReferenceService, ExpectedArtifactService } from 'core/artifact';
import { ExecutionArtifactTab } from 'core/artifact/react/ExecutionArtifactTab';
import { SETTINGS } from 'core/config/settings';
import { Registry } from 'core/registry';
import { SavePipelinesResultsTab } from 'core/pipeline/config/stages/savePipelines/SavePipelinesResultsTab';
import { ExecutionDetailsTasks } from 'core/pipeline/config/stages/common/ExecutionDetailsTasks';
import { SavePipelinesStageConfig } from 'core/pipeline/config/stages/savePipelines/SavePipelinesStageConfig';

if (SETTINGS.feature.versionedProviders) {
  Registry.pipeline.registerStage({
    label: 'Save Pipelines',
    description: 'Saves pipelines defined in an artifact.',
    key: 'savePipelinesFromArtifact',
    component: SavePipelinesStageConfig,
    executionDetailsSections: [ExecutionDetailsTasks, ExecutionArtifactTab, SavePipelinesResultsTab],
    defaultTimeoutMs: 30 * 60 * 1000, // 30 minutes
    artifactExtractor: ExpectedArtifactService.accumulateArtifacts(['pipelinesArtifactId', 'requiredArtifactIds']),
    artifactRemover: ArtifactReferenceService.removeArtifactFromFields(['pipelinesArtifactId', 'requiredArtifactIds']),
  });
}
