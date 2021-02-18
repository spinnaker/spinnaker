import { get } from 'lodash';
import React from 'react';

import { HoverablePopover } from '@spinnaker/core';

import './manifestLabels.less';

// TODO(dpeach) https://github.com/spinnaker/spinnaker/issues/3239
export interface IManifestQosProps {
  manifest?: {
    status?: {
      qosClass?: string;
    };
  };
}

export class ManifestQos extends React.Component<IManifestQosProps> {
  private qosClass(): string {
    return get(this.props, ['manifest', 'status', 'qosClass'], 'Unknown');
  }

  private qosStyle(): string {
    const qosClass = this.qosClass();
    if (qosClass === 'Guaranteed') {
      return 'success';
    } else if (qosClass === 'BestEffort') {
      return 'alert';
    } else {
      return 'warn';
    }
  }

  private description = () => {
    return (
      <div>
        <p>There are three QOS (Quality of Service) classes:</p>
        <p>
          <b>Guaranteed</b>: Pods are considered highest-priority, and will only be killed if they exceed their resource
          requests when other containers require their resources.
        </p>
        <p>
          <b>Burstable</b>: Pods have some minimum guaranteed resources and can exceed them, but are more likely to be
          killed than <b>Guaranteed</b> pods.
        </p>
        <p>
          <b>BestEffort</b>: These are the lowest-priority pods, and will be the first to be evicted when resources run
          out.
        </p>
        <p>
          To understand how to set the QOS class of a pod, read{' '}
          <a href="https://kubernetes.io/docs/tasks/configure-pod-container/quality-service-pod/">
            the Kubernetes documentation
          </a>
          .
        </p>
      </div>
    );
  };

  public render() {
    const style = `sp-badge ${this.qosStyle()}`;
    return (
      <HoverablePopover Component={this.description} title="QOS Class" className={`ephemeral-popover`}>
        <div className={style}>{this.qosClass()}</div>
      </HoverablePopover>
    );
  }
}
