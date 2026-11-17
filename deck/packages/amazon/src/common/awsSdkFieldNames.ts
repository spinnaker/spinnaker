// Copyright 2026 spinnaker.io
// SPDX-License-Identifier: Apache-2.0

// Clouddriver serializes AWS SDK v2 model objects using their builder property names
// (e.g. vpcZoneIdentifier, dnsName). Data cached before that change still carries the
// AWS SDK v1 Jackson names (e.g. vpczoneIdentifier, dnsname). These accessors prefer the
// builder name and fall back to the legacy name.
// legacy: pre-AWS SDK v2 builder names; remove the fallbacks once caches have refreshed

export const getVpcZoneIdentifier = (asg: { vpcZoneIdentifier?: string; vpczoneIdentifier?: string }): string =>
  asg?.vpcZoneIdentifier ?? asg?.vpczoneIdentifier;

export const getDnsName = (loadBalancer: { dnsName?: string; dnsname?: string }): string =>
  loadBalancer?.dnsName ?? loadBalancer?.dnsname;

export const getVpcId = (loadBalancer: { vpcId?: string; vpcid?: string }): string =>
  loadBalancer?.vpcId || loadBalancer?.vpcid;

export const getSslCertificateId = (listener: { sslCertificateId?: string; sslcertificateId?: string }): string =>
  listener?.sslCertificateId ?? listener?.sslcertificateId;

export const getKmsKeyArn = (functionDef: { kmsKeyArn?: string; kmskeyArn?: string }): string =>
  functionDef?.kmsKeyArn ?? functionDef?.kmskeyArn;

export const getOkActions = (alarm: { okActions?: string[]; okactions?: string[] }): string[] =>
  alarm?.okActions ?? alarm?.okactions;
