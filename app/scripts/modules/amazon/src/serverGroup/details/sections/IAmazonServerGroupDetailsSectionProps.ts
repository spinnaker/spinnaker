import { IServerGroupDetailsSectionProps } from '@spinnaker/core';
import { IAmazonServerGroupView } from 'amazon/domain';

export interface IAmazonServerGroupDetailsSectionProps extends IServerGroupDetailsSectionProps {
  serverGroup: IAmazonServerGroupView;
}
