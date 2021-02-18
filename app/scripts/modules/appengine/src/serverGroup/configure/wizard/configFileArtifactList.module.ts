import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { ConfigFileArtifactList } from './ConfigFileArtifactList';

export const CONFIG_FILE_ARTIFACT_LIST = 'spinnaker.appengine.configFileArtifactList.component';

module(CONFIG_FILE_ARTIFACT_LIST, []).component(
  'configFileArtifactList',
  react2angular(withErrorBoundary(ConfigFileArtifactList, 'configFileArtifactList'), [
    'configArtifacts',
    'pipeline',
    'stage',
    'updateConfigArtifacts',
  ]),
);
