import { useApplicationContextSafe } from '../..';
import { useFetchResourceStatusQuery } from '../graphql/graphql-sdk';

export const useIsUpdatingResources = (environment: string) => {
  const app = useApplicationContextSafe();
  const { data } = useFetchResourceStatusQuery({ variables: { appName: app.name } });
  const resources = data?.application?.environments.find((env) => env.name === environment)?.state.resources;
  return resources?.some((resource) => resource.state?.status !== 'UP_TO_DATE');
};
