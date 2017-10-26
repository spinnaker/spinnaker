# keel

Spinnaker's declarative service.

## test payload

`PUT https://localhost:8087/intents`

```json
{
	"intents": [
		{
			"kind": "Parrot",
			"schema": "1",
			"spec": {
				"application": "keel",
				"description": "hello"
			},
			"status": "ACTIVE",
			"id": "$KIND:${spec.name}"
		}
	],
	"dryRun": true
}
```
