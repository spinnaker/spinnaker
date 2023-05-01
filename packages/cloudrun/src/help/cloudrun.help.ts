import { HelpContentsRegistry } from '@spinnaker/core';

const helpContents = [
  {
    key: 'cloudrun.serverGroup.stack',
    value:
      '(Optional) <b>Stack</b> is one of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.',
  },

  {
    key: 'cloudrun.serverGroup.file',
    value: `<pre>
    apiVersion: serving.knative.dev/v1
    kind: Service
    metadata:
        name: spinappcloud1
        namespace: '135005621049'
        labels:
            cloud.googleapis.com/location: us-central1
    annotations:
        run.googleapis.com/client-name: cloud-console
        serving.knative.dev/creator: kiran@opsmx.io
        serving.knative.dev/lastModifier: kiran@opsmx.io
        client.knative.dev/user-image: us-docker.pkg.dev/cloudrun/container/hello
        run.googleapis.com/ingress-status: all
    spec:
        template:
        metagoogleapis.com/ingress: all
        run.data:
        name: spinappcloud1
    annotations:
        run.googleapis.com/client-name: cloud-console
        autoscaling.knative.dev/minScale: '1'
        autoscaling.knative.dev/maxScale: '3'
    spec:
        containerConcurrency: 80
        timeoutSeconds: 200
        serviceAccountName:spinnaker-cloudrun-account@my-orbit-project-71824.iam.gserviceaccount.com
        containers:
           - image:us-docker.pkg.dev/cloudrun/container/hello
        ports:
           - name: http1
        containerPort: 8080
        resources:
        limits:
        cpu: 1000m
        memory: 256Mi  
</pre>
    `,
  },

  {
    key: 'cloudrun.serverGroup.detail',
    value:
      ' (Optional) <b>Detail</b> is a string of free-form alphanumeric characters and hyphens to describe any other variables.',
  },
  {
    key: 'cloudrun.serverGroup.configFiles',
    value: `<p> The contents of a Cloud Run Service yaml </p>`,
  },
  {
    key: 'cloudrun.loadBalancer.allocations',
    value: 'An allocation is the percent of traffic directed to a server group.',
  },
  {
    key: 'cloudrun.manifest.source',
    value: `
    <p>Where the manifest file content is read from.</p>
    <p>
      <b>text:</b> The manifest is supplied statically to the pipeline from the below text-box.
    </p>
    <p>
      <b>artifact:</b> The manifest is read from an artifact supplied/created upstream. The expected artifact must be referenced here, and will be bound at runtime.
    </p>`,
  },
  {
    key: 'cloudrun.manifest.expectedArtifact',
    value:
      'The artifact that is to be applied to the Cloud Run account for this stage.  The artifact should represent a valid Cloud Run manifest.',
  },
  {
    key: 'cloudrun.manifest.skipExpressionEvaluation',
    value:
      '<p>Skip SpEL expression evaluation of the manifest artifact in this stage. Can be paired with the "Evaluate SpEL expressions in overrides at bake time" option in the Bake Manifest stage when baking a third-party manifest artifact with expressions not meant for Spinnaker to evaluate as SpEL.</p>',
  },
  {
    key: 'cloudrun.manifest.requiredArtifactsToBind',
    value:
      'These artifacts must be present in the context for this stage to successfully complete. Artifacts specified will be <a href="https://www.spinnaker.io/reference/artifacts/in-cloudrun-v2/#binding-artifacts-in-manifests" target="_blank">bound to the deployed manifest.</a>',
  },
  {
    key: 'cloudrun.manifest.account',
    value: `
    <p>A Spinnaker account corresponds to a physical Cloud Run cluster. If you are unsure which account to use, talk to your Spinnaker admin.</p>
    `,
  },
];

helpContents.forEach((entry) => HelpContentsRegistry.register(entry.key, entry.value));
