import * as React from 'react';

import { ILoadBalancerModalProps, noop } from '@spinnaker/core';
import {
  AmazonLoadBalancerChoiceModal,
  IAmazonLoadBalancerChoiceModalProps,
  LoadBalancerTypes,
} from '@spinnaker/amazon';

export class TitusLoadBalancerChoiceModal extends React.Component<ILoadBalancerModalProps> {
  public static defaultProps: Partial<ILoadBalancerModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(props: IAmazonLoadBalancerChoiceModalProps): Promise<void> {
    // Titus is not compatible with NLBs as it requires ip target groups which NLBs do not have
    return AmazonLoadBalancerChoiceModal.show({
      ...props,
      choices: LoadBalancerTypes.filter(lb => lb.type !== 'network'),
    });
  }
}
