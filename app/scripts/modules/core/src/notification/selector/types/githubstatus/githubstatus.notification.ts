import { Registry } from 'core/registry';

import { GithubNotificationType } from './GithubNotificationType';

Registry.pipeline.registerNotification({
  component: GithubNotificationType,
  key: 'githubStatus',
  label: 'Github Status',
});
