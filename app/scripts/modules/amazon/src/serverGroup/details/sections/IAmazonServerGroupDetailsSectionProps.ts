import { IAmazonServerGroupView } from 'amazon/domain';

import { IServerGroupDetailsSectionProps } from '@spinnaker/core';

export interface IAmazonServerGroupDetailsSectionProps extends IServerGroupDetailsSectionProps {
  serverGroup: IAmazonServerGroupView;
}
