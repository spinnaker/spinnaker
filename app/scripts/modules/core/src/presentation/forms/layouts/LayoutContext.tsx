import * as React from 'react';
import { StandardFieldLayout } from './StandardFieldLayout';

export const { Provider: LayoutProvider, Consumer: LayoutConsumer } = React.createContext(StandardFieldLayout);
