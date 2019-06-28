import * as React from 'react';
import { IFieldLayoutProps } from '../interface';
import { StandardFieldLayout } from './StandardFieldLayout';

export const LayoutContext = React.createContext<React.ComponentType<IFieldLayoutProps>>(StandardFieldLayout);
export const { Provider: LayoutProvider, Consumer: LayoutConsumer } = LayoutContext;
