import { ImportDeliveryConfigExecutionDetails } from './ImportDeliveryConfigExecutionDetails';
import { ImportDeliveryConfigStageConfig } from './ImportDeliveryConfigStageConfig';
import { ExecutionDetailsTasks } from '../common';
import { SETTINGS } from '../../../../config';
import { Registry } from '../../../../registry';
import { IUpstreamFlagProvidedValidationConfig } from '../../validation/upstreamHasFlagValidator.builder';

if (SETTINGS.feature.managedDelivery) {
  Registry.pipeline.registerStage({
    label: 'Import Delivery Config',
    description:
      "Retrieve a Delivery Config manifest from the git repository configured in the pipeline's trigger, then update it in Spinnaker.",
    extendedDescription: `<a target="_blank" href="https://www.spinnaker.io/guides/user/managed-delivery/git-based-workflows/">
      <span class="small glyphicon glyphicon-file"></span> Documentation</a>`,
    key: 'importDeliveryConfig',
    restartable: false,
    component: ImportDeliveryConfigStageConfig,
    executionDetailsSections: [ImportDeliveryConfigExecutionDetails, ExecutionDetailsTasks],
    validators: [
      {
        type: 'repositoryInformationProvided',
        getMessage: (labels: any[]) => `
          This stage requires one of the following triggers to locate your Delivery Config manifest:
          <ul>${labels.map((label) => `<li>${label}</li>`)}</ul>
          `,
      } as IUpstreamFlagProvidedValidationConfig,
    ],
  });
}
