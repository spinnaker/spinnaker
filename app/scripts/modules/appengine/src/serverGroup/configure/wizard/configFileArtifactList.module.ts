import { module } from 'angular';
import { react2angular } from 'react2angular';
import { ConfigFileArtifactList } from './ConfigFileArtifactList';

export const CONFIG_FILE_ARTIFACT_LIST = 'spinnaker.appengine.configFileArtifactList.component';

module(CONFIG_FILE_ARTIFACT_LIST, []).component(
  'configFileArtifactList',
  react2angular(ConfigFileArtifactList, [
    'configArtifacts',
    'pipeline',
    'stage',
    'updateConfigArtifacts',
    'updatePipeline',
  ]),
);
