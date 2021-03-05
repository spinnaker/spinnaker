import React from 'react';

import { Application } from './application.model';

export const ApplicationContext = React.createContext<Application | undefined>(undefined);

export const ApplicationContextProvider: React.FC<{ app: Application }> = ({ app, children }) => {
  return <ApplicationContext.Provider value={app}>{children}</ApplicationContext.Provider>;
};
