# Deprecating Fields

When a field is no longer read by any Spinnaker microservice, the field should
be deprecated in Halyard so users know it is safe to remove from their
halconfig.

To deprecate a field, annotate it with `ValidForSpinnakerVersion`, indicating
the first Spinnaker version in which the field is no longer necessary as the
`upperBound`. Optionally, supply a message indicating why the field is no
longer required as the `tooHighMessage`.

See the `artifacts` and `artifactsRewrite` fields in the `Features` class as
examples of deprecated fields.
