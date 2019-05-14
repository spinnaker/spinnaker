import { GitTrigger } from './GitTrigger';

import { ArtifactTypePatterns, excludeAllTypesExcept } from 'core/artifact';
import { Registry } from 'core/registry';
import { GitTriggerExecutionStatus } from './GitTriggerExecutionStatus';

Registry.pipeline.registerTrigger({
  component: GitTrigger,
  description: 'Executes the pipeline on a git push',
  executionStatusComponent: GitTriggerExecutionStatus,
  excludedArtifactTypePatterns: excludeAllTypesExcept(
    ArtifactTypePatterns.GITHUB_FILE,
    ArtifactTypePatterns.GITLAB_FILE,
    ArtifactTypePatterns.BITBUCKET_FILE,
  ),
  key: 'git',
  label: 'Git',
  validators: [
    {
      type: 'serviceAccountAccess',
      message: `You do not have access to the service account configured in this pipeline's git trigger.
                You will not be able to save your edits to this pipeline.`,
      preventSave: true,
    },
  ],
});
