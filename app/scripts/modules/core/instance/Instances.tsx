import * as React from 'react';
import { angular2react } from 'angular2react';

import { Instance } from 'core/domain';
import { InstancesWrapperComponent } from './instances.component';
import { ReactInjector } from 'core/react.module';

interface IProps {
  instances: Instance[];
  highlight?: string;
}

export let Instances: React.ComponentClass<IProps> = undefined;
ReactInjector.give(($injector: any) => Instances = angular2react('instancesWrapper', new InstancesWrapperComponent(), $injector) as any);
