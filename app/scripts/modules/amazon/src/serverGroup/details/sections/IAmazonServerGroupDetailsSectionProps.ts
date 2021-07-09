import { IServerGroupDetailsSectionProps } from '@spinnaker/core';
import { IAmazonServerGroupView } from '../../../domain';

export interface IAmazonServerGroupDetailsSectionProps extends IServerGroupDetailsSectionProps {
  serverGroup: IAmazonServerGroupView;
}
