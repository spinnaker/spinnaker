import { ApolloClient, HttpLink, InMemoryCache } from '@apollo/client';
import { REST } from '../../api';

const customFetch: typeof fetch = (uri, options) => {
  return new Promise((resolve, reject) => {
    REST(uri as string)
      .post(options?.body)
      .then((res) => {
        const result: Partial<Response> = {
          ok: true,
          status: 200,
          text: () =>
            new Promise((innerResolve) => {
              innerResolve(JSON.stringify(res));
            }),
        };
        resolve(result as Response);
      })
      .catch((e) => reject(e));
  });
};

export const createApolloClient = () => {
  const client = new ApolloClient({
    cache: new InMemoryCache(),
  });
  const link = new HttpLink({ uri: '/managed/graphql', fetch: customFetch });
  client.setLink(link);
  const onRefresh = () => client.reFetchObservableQueries();
  return { client, onRefresh };
};
