import { ConcourseExecutionDetails } from './ConcourseExecutionDetails';
import { ConcourseStageConfig } from './ConcourseStageConfig';
import { Registry } from '../../../../registry';

Registry.pipeline.registerStage({
  label: 'Concourse',
  description: 'Runs a Concourse job',
  key: 'concourse',
  defaults: {
    failOnFailedExpressions: true,
  },
  component: ConcourseStageConfig,
  executionDetailsSections: [ConcourseExecutionDetails],
  strategy: true,
  validators: [
    { type: 'requiredField', fieldName: 'master' },
    { type: 'requiredField', fieldName: 'teamName', fieldLabel: 'Team Name' },
    { type: 'requiredField', fieldName: 'pipelineName', fieldLabel: 'Pipeline Name' },
    { type: 'requiredField', fieldName: 'resourceName', fieldLabel: 'Resource Name' },
  ],
});
