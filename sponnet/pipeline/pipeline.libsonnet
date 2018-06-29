{
  pipeline():: {
    limitConcurrent: true,
    keepWaitingPipelines: false,
    stages: [],
    triggers: [],
    withApplication(application):: self + {application: application},
    withName(name):: self + {name: name},
    withStages(stages):: self + if std.type(stages) == "array" then {stages: stages} else {stages: [stages]},
    withTriggers(triggers):: self + if std.type(triggers) == "array" then {triggers: triggers} else {triggers: [triggers]},
  },

  moniker(app, cluster):: {
    app: app,
    cluster: cluster,
  },

  // artifacts

  artifact(type):: {
    type: type,
    withName(name):: self + {name: name},
    withVersion(version):: self + {version: version},
    withReference(reference):: self + {reference: reference},
    withLocation(location):: self + {location: location},
  },

  local artifact = self.artifact,
  artifacts:: {
    dockerImage():: artifact("docker/image"),
    githubFile():: artifact("github/file"),
    gcsObject():: artifact("gcs/object"),
  },

  expectedArtifact(id):: {
    id: id,
    withMatchArtifact(matchArtifact):: self + {matchArtifact: matchArtifact},
    withDefaultArtifact(defaultArtifact):: self + {defaultArtifact: defaultArtifact},
    withUsePriorArtifact(usePriorArtifact):: self + {usePriorArtifact: usePriorArtifact},
    withUseDefaultArtifact(useDefaultArtifact):: self + {useDefaultArtifact: useDefaultArtifact},
  },

  // triggers

  trigger():: {
    enabled: true,
    withType(type):: self + {type: type},
    withExpectedArtifacts(expectedArtifacts):: self + if std.type(expectedArtifacts) == "array" then {expectedArtifactIds: std.map(function(expectedArtifact) expectedArtifact.id, expectedArtifacts)} else {expectedArtifactIds: [expectedArtifacts.id]},
  },

  // stages

  stage(name, type):: {
    refId: name,
    name: name,
    type: type,
    requisiteStageRefIds: [],
    withRequisiteStages(stages):: self + if std.type(stages) == "array" then {requisiteStageRefIds: std.map(function(stage) stage.refId, stages)} else {requisiteStageRefIds: [stages.refId]}
  },

  local stage = self.stage,
  stages:: {
    wait(name):: stage(name, "wait") {
      withWaitTime(waitTime):: self + {waitTime: waitTime},
    },
    deploy_manifest(name):: stage(name, "deployManifest") {
      cloudProvider: "kubernetes",
      source: "text",
      withAccount(account):: self + {account: account},
      withManifests(manifests):: self + if std.type(manifests) == "array" then {manifests: manifests} else {manifests: [manifests]},
      withMoniker(moniker):: self + {moniker: moniker},
    },
  },
}
