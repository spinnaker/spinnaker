# Verification links

A VerificationState object has a `link` field.
This represents a URL that is exposed via Deck that allows the user to view more information about the state of the verification (e.g., test log output).

## Serving the link over the API

Verification links appear in the response to the `ApplicationController.get` call (`/application/{application}`).
When served over the API, the links appear inside of `VerificationSummary` objects.

## Generating the link

The VerificationEvaluator subclass is responsible for computing the link in the `evaluate` method.
