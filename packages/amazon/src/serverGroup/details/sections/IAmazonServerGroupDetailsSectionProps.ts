import type { IServerGroupDetailsSectionProps } from '@spinnaker/core';
import type { IAmazonServerGroupView } from '../../../domain';

export interface IAmazonServerGroupDetailsSectionProps extends IServerGroupDetailsSectionProps {
  serverGroup: IAmazonServerGroupView;
}
