import { logger } from 'core/utils';
import { useApplicationContext } from '../../presentation/hooks/useApplicationContext.hook';

interface LogProps {
  category: string;
  action: string;
  application?: string;
  label?: string;
}

export const logEvent = ({ category, action, application, label }: LogProps) => {
  logger.log({
    category,
    action: action,
    data: { label: (application ? `${application}:` : ``) + label },
  });
};

export const useLogEvent = (category: string) => {
  const app = useApplicationContext();
  return (props: Omit<LogProps, 'application' | 'category'>) => {
    logEvent({ ...props, category, application: app?.name });
  };
};

export const logCategories = {
  artifactDetails: 'Environments - artifact details',
};
