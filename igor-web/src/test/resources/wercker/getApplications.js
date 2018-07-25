[
  {
    "id": "58344deddd22a50100526763",
    "url": "http://localhost:3000/api/v3/applications/wercker/ark",
    "name": "ark",
    "owner": {
      "userId": "5530d21f3151d2c55400006f",
      "name": "wercker",
      "meta": {
        "username": "wercker",
        "type": "organization",
        "werckerEmployee": false
      }
    },
    "createdAt": "2016-11-22T13:53:49.807Z",
    "updatedAt": "2016-12-01T15:22:21.865Z",
    "privacy": "private",
    "stack": 6,
    "theme": "Amethyst",
    "pipelines": [
      {
        "id": "58344deed35d1401003f18c7",
        "url": "http://localhost:3000/api/v3/pipelines/58344deed35d1401003f18c7",
        "createdAt": "2016-11-22T13:53:50.383Z",
        "name": "build",
        "permissions": "public",
        "pipelineName": "build",
        "setScmProviderStatus": true,
        "type": "git"
      },
      {
        "id": "583471d9dd22a501005268e5",
        "url": "http://localhost:3000/api/v3/pipelines/583471d9dd22a501005268e5",
        "createdAt": "2016-11-22T16:27:05.816Z",
        "name": "push-quay",
        "permissions": "read",
        "pipelineName": "push-quay",
        "setScmProviderStatus": false,
        "type": "pipeline"
      },
      {
        "id": "58347282dd22a501005268e7",
        "url": "http://localhost:3000/api/v3/pipelines/58347282dd22a501005268e7",
        "createdAt": "2016-11-22T16:29:54.792Z",
        "name": "deploy-kube-staging",
        "permissions": "read",
        "pipelineName": "deploy-kube",
        "setScmProviderStatus": false,
        "type": "pipeline"
      },
      {
        "id": "585d3f6f84027801002a59f5",
        "url": "http://localhost:3000/api/v3/pipelines/585d3f6f84027801002a59f5",
        "createdAt": "2016-12-23T15:14:55.599Z",
        "name": "deploy-kube-production",
        "permissions": "read",
        "pipelineName": "deploy-kube",
        "setScmProviderStatus": false,
        "type": "pipeline"
      }
    ]
  },
  {
    "id": "57bda6e9e382a101002ce7c1",
    "url": "http://localhost:3000/api/v3/applications/wercker/auth",
    "name": "auth",
    "owner": {
      "userId": "5530d21f3151d2c55400006f",
      "name": "wercker",
      "meta": {
        "username": "wercker",
        "type": "organization",
        "werckerEmployee": false
      }
    },
    "createdAt": "2016-08-24T13:53:45.870Z",
    "updatedAt": "2016-08-24T13:53:45.869Z",
    "privacy": "private",
    "stack": 6,
    "theme": "Cosmopolitan",
    "pipelines": [
      {
        "id": "57bda6ea8404640100e7004f",
        "url": "http://localhost:3000/api/v3/pipelines/57bda6ea8404640100e7004f",
        "createdAt": "2016-08-24T13:53:46.253Z",
        "name": "build",
        "permissions": "public",
        "pipelineName": "build",
        "setScmProviderStatus": true,
        "type": "git"
      },
      {
        "id": "57bdbff48404640100e700e1",
        "url": "http://localhost:3000/api/v3/pipelines/57bdbff48404640100e700e1",
        "createdAt": "2016-08-24T15:40:36.402Z",
        "name": "push-quay",
        "permissions": "read",
        "pipelineName": "push-quay",
        "setScmProviderStatus": false,
        "type": "pipeline"
      },
      {
        "id": "57bdbff48404640100e700e3",
        "url": "http://localhost:3000/api/v3/pipelines/57bdbff48404640100e700e3",
        "createdAt": "2016-08-24T15:40:36.694Z",
        "name": "deploy-kube-staging",
        "permissions": "read",
        "pipelineName": "deploy-kube",
        "setScmProviderStatus": false,
        "type": "pipeline"
      },
      {
        "id": "585d3532180d780100492891",
        "url": "http://localhost:3000/api/v3/pipelines/585d3532180d780100492891",
        "createdAt": "2016-12-23T14:31:14.953Z",
        "name": "deploy-kube-production",
        "permissions": "read",
        "pipelineName": "deploy-kube",
        "setScmProviderStatus": false,
        "type": "pipeline"
      }
    ]
  },
  {
    "id": "57fe0a5ce644cd01001e1de3",
    "url": "http://localhost:3000/api/v3/applications/wercker/bcarter",
    "name": "bcarter",
    "owner": {
      "userId": "5530d21f3151d2c55400006f",
      "name": "wercker",
      "meta": {
        "username": "wercker",
        "type": "organization",
        "werckerEmployee": false
      }
    },
    "createdAt": "2016-10-12T10:03:08.278Z",
    "updatedAt": "2016-10-12T10:03:08.278Z",
    "privacy": "private",
    "stack": 6,
    "theme": "Appletini",
    "pipelines": [
      {
        "id": "57fe0a5c56f1d70100076cc5",
        "url": "http://localhost:3000/api/v3/pipelines/57fe0a5c56f1d70100076cc5",
        "createdAt": "2016-10-12T10:03:08.729Z",
        "name": "build",
        "permissions": "public",
        "pipelineName": "build",
        "setScmProviderStatus": true,
        "type": "git"
      }
    ]
  },
  {
    "id": "588f5baf5e93a901000fe15c",
    "url": "http://localhost:3000/api/v3/applications/wercker/bitbucket-hook",
    "name": "bitbucket-hook",
    "owner": {
      "userId": "5530d21f3151d2c55400006f",
      "name": "wercker",
      "meta": {
        "username": "wercker",
        "type": "organization",
        "werckerEmployee": false
      }
    },
    "createdAt": "2017-01-30T15:28:47.276Z",
    "updatedAt": "2018-04-05T08:32:41.155Z",
    "privacy": "private",
    "stack": 6,
    "theme": "Onyx",
    "pipelines": [
      {
        "id": "588f5baf5e93a901000fe15f",
        "url": "http://localhost:3000/api/v3/pipelines/588f5baf5e93a901000fe15f",
        "createdAt": "2017-01-30T15:28:47.588Z",
        "name": "build",
        "permissions": "public",
        "pipelineName": "build",
        "setScmProviderStatus": true,
        "type": "git"
      }
    ]
  },
  {
    "id": "5989b9136f0ad8010098862c",
    "url": "http://localhost:3000/api/v3/applications/wercker/blackbox",
    "name": "blackbox",
    "owner": {
      "userId": "5530d21f3151d2c55400006f",
      "name": "wercker",
      "meta": {
        "username": "wercker",
        "type": "organization",
        "werckerEmployee": false
      }
    },
    "createdAt": "2017-08-08T13:13:55.471Z",
    "updatedAt": "2017-08-08T13:13:55.471Z",
    "privacy": "private",
    "stack": 6,
    "theme": "Amethyst",
    "pipelines": [
      {
        "id": "5989b9133b59d401009f2900",
        "url": "http://localhost:3000/api/v3/pipelines/5989b9133b59d401009f2900",
        "createdAt": "2017-08-08T13:13:55.951Z",
        "name": "build",
        "permissions": "public",
        "pipelineName": "build",
        "setScmProviderStatus": true,
        "type": "git"
      },
      {
        "id": "5989bfb83b59d401009f294e",
        "url": "http://localhost:3000/api/v3/pipelines/5989bfb83b59d401009f294e",
        "createdAt": "2017-08-08T13:42:16.047Z",
        "name": "push-quay",
        "permissions": "read",
        "pipelineName": "push-quay",
        "setScmProviderStatus": false,
        "type": "pipeline"
      },
      {
        "id": "5989cda13b59d401009f2ad4",
        "url": "http://localhost:3000/api/v3/pipelines/5989cda13b59d401009f2ad4",
        "createdAt": "2017-08-08T14:41:37.025Z",
        "name": "deploy-kube-staging",
        "permissions": "read",
        "pipelineName": "deploy-kube",
        "setScmProviderStatus": false,
        "type": "pipeline"
      },
      {
        "id": "5989ce5b3b59d401009f2ad6",
        "url": "http://localhost:3000/api/v3/pipelines/5989ce5b3b59d401009f2ad6",
        "createdAt": "2017-08-08T14:44:43.146Z",
        "name": "deploy-kube-production",
        "permissions": "read",
        "pipelineName": "deploy-kube",
        "setScmProviderStatus": false,
        "type": "pipeline"
      }
    ]
  }
]