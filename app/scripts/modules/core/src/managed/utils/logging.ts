import React from 'react';

import { logger } from 'core/utils';

import { useApplicationContext } from '../../presentation/hooks/useApplicationContext.hook';

interface LogProps {
  category: string;
  action: string;
  application?: string;
  label?: string;
  data?: Record<string, any>;
}

const CATEGORY_PREFIX = 'MD__';

export const useLogEvent = (category: string, action?: string) => {
  const app = useApplicationContext();
  const logFn = React.useCallback(
    (props?: Omit<Partial<LogProps>, 'application' | 'category'>) => {
      logger.log({
        category: `${CATEGORY_PREFIX}${category}`,
        action: props?.action || action || '',
        data: { label: props?.label, application: app?.name, ...props?.data },
      });
    },
    [app?.name, category, action],
  );
  return logFn;
};

export const logCategories = {
  artifactDetails: 'Environments - artifact details',
};
