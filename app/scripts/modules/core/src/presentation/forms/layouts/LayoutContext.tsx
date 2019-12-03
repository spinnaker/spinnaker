import React from 'react';
import { ILayoutProps } from './interface';
import { StandardFieldLayout } from './StandardFieldLayout';

export const LayoutContext = React.createContext<React.ComponentType<ILayoutProps>>(StandardFieldLayout);
export const { Provider: LayoutProvider, Consumer: LayoutConsumer } = LayoutContext;
