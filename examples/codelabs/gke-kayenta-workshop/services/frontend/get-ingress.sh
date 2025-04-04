#!/usr/bin/env bash

kubectl get svc frontend -n production -o jsonpath="{.status.loadBalancer.ingress[0].ip}"

echo ""
