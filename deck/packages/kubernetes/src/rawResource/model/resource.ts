interface IResource {
  name: string;
  labels: {
    [key: string]: string;
  };
}

interface IApiKubernetesResource {
  account: string;
  apiVersion: string;
  displayName: string;
  kind: string;
  labels: Record<string, string>;
  moniker: Record<string, string>;
  name: string;
  namespace: string;
  region: string;
}
