import * as React from 'react';
import { angular2react } from 'angular2react';

import { IInstance } from 'core/domain';
import { InstancesWrapperComponent } from './instances.component';
import { ReactInjector } from 'core/react.module';

interface IProps {
  instances: IInstance[];
  highlight?: string;
}

export let Instances: React.ComponentClass<IProps> = undefined;
ReactInjector.give(($injector: any) => Instances = angular2react('instancesWrapper', new InstancesWrapperComponent(), $injector) as any);
