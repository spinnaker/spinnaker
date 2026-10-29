#!/usr/bin/env bash
# Usage: trim-image-sbom.sh <project> <variant> <image-digest>
#
# Extracts the buildx-generated SPDX SBOM for a pushed image and trims it so it fits within the
# 16 MiB attestation predicate limit.  The full document lists every file in the image (which can
# exceed the limit); keep the package inventory and drop the per-file entries and their relationships.
set -euo pipefail

project=$1
variant=$2
digest=$3

full="${project}-${variant}-image-sbom.full.spdx.json"
trimmed="${project}-${variant}-image-sbom.spdx.json"

docker buildx imagetools inspect "ghcr.io/spinnaker/${project}@${digest}" --format '{{json (index .SBOM "linux/amd64").SPDX}}' > "$full"

jq -c '
  del(.files)
  | .relationships |= map(select((.spdxElementId | startswith("SPDXRef-File")) or (.relatedSpdxElement | startswith("SPDXRef-File")) | not))
' "$full" > "$trimmed"
rm "$full"
ls -l "$trimmed"
