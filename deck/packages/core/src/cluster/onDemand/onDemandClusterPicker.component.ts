import { module } from 'angular';
import { OnDemandClusterPicker } from './OnDemandClusterPicker';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const ON_DEMAND_CLUSTER_PICKER_COMPONENT = 'spinnaker.core.cluster.onDemandClusterPicker.component';
export const onDemandClusterPickerComponent = angularComponentFromReact(
  OnDemandClusterPicker,
  'onDemandClusterPicker',
  ['application'],
);

module(ON_DEMAND_CLUSTER_PICKER_COMPONENT, []).component('onDemandClusterPicker', onDemandClusterPickerComponent);
