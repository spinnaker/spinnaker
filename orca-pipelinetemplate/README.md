UNSTABLE

Note - this module is currently a work in progress and under active development.

Expect breaking changes in the [model](https://github.com/spinnaker/dcd-spec) and
execution engine backing this feature for the time being.

# Enabling Pipeline Templates

In your orca configuration, set the configuration value `pipelineTemplate.enabled`
to `true`.

## Quickstart

First create a Pipeline Template. In this example, we're just creating a wait stage.

```yaml
# https://gist.githubusercontent.com/robzienert/04f326f3077df176b1788b30e06ed981/raw/b9eed8643e9028d27f21c3dee7ca3b0b1f8c9fee/barebones.yml
schema: "1"
id: barebones
stages:
- id: wait
  type: wait
  config:
    waitTime: 5
```

Then start the pipeline by sending a start `managedTemplate` pipeline request with the following payload:


```json
{
	"type": "templatedPipeline",
	"config": {
	  "schema": "1",
		"pipeline":{
			"name": "My fancy DCD pipeline",
			"application": "yourAppName",
			"template":{
				"source":"https://gist.githubusercontent.com/robzienert/04f326f3077df176b1788b30e06ed981/raw/b9eed8643e9028d27f21c3dee7ca3b0b1f8c9fee/barebones.yml"
			}
		}
	}
}
```

This can be either sent directly into Orca at `http://orca-url/orchestrate` or
via Gate at `http://gate-url/pipelines/start`.
