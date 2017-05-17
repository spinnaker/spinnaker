import {module} from 'angular';
import {react2angular} from 'react2angular';
import {CreatePipelineModal} from './CreatePipelineModal';

export const CREATE_PIPELINE_MODAL = 'spinnaker.core.pipeline.create.modal';
module(CREATE_PIPELINE_MODAL, [])
  .component('createPipelineModal', react2angular(CreatePipelineModal, ['show', 'application', 'showCallback', 'pipelineSavedCallback']));
