import React from 'react';

import { useApplicationContext } from '../../presentation/hooks/useApplicationContext.hook';
import { logger, LoggerEvent } from '../../utils';

interface LogProps extends LoggerEvent {
  application?: string;
  label?: string;
}

const CATEGORY_PREFIX = 'MD__';

export const useLogEvent = (category: string, action?: string) => {
  const app = useApplicationContext();
  const logFn = React.useCallback(
    (props?: Omit<Partial<LogProps>, 'application' | 'category'>) => {
      logger.log({
        ...props,
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
