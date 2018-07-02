local sponnet = import "pipeline.libsonnet";
local kubeutils = import "kubeutils.libsonnet";
local deployment = import "deployment.json";

local canary_deployment = kubeutils.canary(deployment);
local account = "staging-demo";
local app = "cluster";
local moniker = sponnet.moniker(app, "some-cluster");

local wait = sponnet.stages
  .wait("Wait")
  .withWaitTime(30);

local manifest_baseline = sponnet.stages
  .deploy_manifest("Deploy a manifest")
  .withManifests(deployment)
  .withAccount(account)
  .withMoniker(moniker)
  .withRequisiteStages(wait);

local manifest_canary = sponnet.stages
  .deploy_manifest("Deploy a canary manifest")
  .withManifests(canary_deployment)
  .withAccount(account)
  .withMoniker(moniker)
  .withRequisiteStages(wait);

sponnet.pipeline()
  .withStages([wait, manifest_baseline, manifest_canary])
  .withName("Demo pipeline")
  .withApplication(app)
