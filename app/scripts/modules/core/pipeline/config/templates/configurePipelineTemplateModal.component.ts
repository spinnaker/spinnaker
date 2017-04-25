import {module} from 'angular';
import {react2angular} from 'react2angular';
import {ConfigurePipelineTemplateModal} from './ConfigurePipelineTemplateModal';

export const CONFIGURE_PIPELINE_TEMPLATE_MODAL = 'spinnaker.core.pipeline.configureTemplate.modal';
module(CONFIGURE_PIPELINE_TEMPLATE_MODAL, [])
  .component('configurePipelineTemplateModal', react2angular(ConfigurePipelineTemplateModal, ['show', 'template']));
