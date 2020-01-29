import { INotification } from 'core/domain';

export const mockPipelineNotification: INotification = {
  level: 'pipline',
  type: 'slack',
  when: ['pipeline.complete', 'pipeline.failed'],
  message: {
    'pipeline.complete': 'Complete',
    'pipeline.failed': 'Failed',
  },
};
