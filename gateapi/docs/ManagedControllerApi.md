# \ManagedControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**CreatePinUsingPOST**](ManagedControllerApi.md#CreatePinUsingPOST) | **Post** /managed/application/{application}/pin | Create a pin for an artifact in an environment
[**DeleteManifestByAppUsingDELETE**](ManagedControllerApi.md#DeleteManifestByAppUsingDELETE) | **Delete** /managed/application/{application}/config | Delete a delivery config manifest for an application
[**DeleteManifestUsingDELETE**](ManagedControllerApi.md#DeleteManifestUsingDELETE) | **Delete** /managed/delivery-configs/{name} | Delete a delivery config manifest
[**DeletePinUsingDELETE**](ManagedControllerApi.md#DeletePinUsingDELETE) | **Delete** /managed/application/{application}/pin/{targetEnvironment} | Unpin one or more artifact(s) in an environment. If the &#x60;reference&#x60; parameter is specified, only the corresponding artifact will be unpinned. If it&#39;s omitted, all pinned artifacts in the environment will be unpinned.
[**DeleteVetoUsingDELETE**](ManagedControllerApi.md#DeleteVetoUsingDELETE) | **Delete** /managed/application/{application}/veto/{targetEnvironment}/{reference}/{version} | Remove veto of an artifact version in an environment
[**DiffManifestUsingPOST**](ManagedControllerApi.md#DiffManifestUsingPOST) | **Post** /managed/delivery-configs/diff | Ad-hoc validate and diff a config manifest
[**DiffResourceUsingPOST**](ManagedControllerApi.md#DiffResourceUsingPOST) | **Post** /managed/resources/diff | Ad-hoc validate and diff a resource
[**ExportResourceUsingGET**](ManagedControllerApi.md#ExportResourceUsingGET) | **Get** /managed/resources/export/artifact/{cloudProvider}/{account}/{clusterName} | Generates an artifact definition based on the artifact used in a running cluster
[**ExportResourceUsingGET1**](ManagedControllerApi.md#ExportResourceUsingGET1) | **Get** /managed/resources/export/{cloudProvider}/{account}/{type}/{name} | Generate a keel resource definition for a deployed cloud resource
[**GetAdoptionReportUsingGET**](ManagedControllerApi.md#GetAdoptionReportUsingGET) | **Get** /managed/reports/adoption | Get a report of Managed Delivery adoption
[**GetApplicationDetailsUsingGET**](ManagedControllerApi.md#GetApplicationDetailsUsingGET) | **Get** /managed/application/{application} | Get managed details about an application
[**GetConfigByUsingGET**](ManagedControllerApi.md#GetConfigByUsingGET) | **Get** /managed/application/{application}/config | Get the delivery config associated with an application
[**GetConstraintStateUsingGET**](ManagedControllerApi.md#GetConstraintStateUsingGET) | **Get** /managed/application/{application}/environment/{environment}/constraints | List up-to {limit} current constraint states for an environment
[**GetEnvironmentsUsingGET**](ManagedControllerApi.md#GetEnvironmentsUsingGET) | **Get** /managed/environments/{application} | Get current environment details
[**GetManifestArtifactsUsingGET**](ManagedControllerApi.md#GetManifestArtifactsUsingGET) | **Get** /managed/delivery-configs/{name}/artifacts | Get the status of each version of each artifact in each environment
[**GetManifestUsingGET**](ManagedControllerApi.md#GetManifestUsingGET) | **Get** /managed/delivery-configs/{name} | Get a delivery config manifest
[**GetManifestYamlUsingGET**](ManagedControllerApi.md#GetManifestYamlUsingGET) | **Get** /managed/delivery-configs/{name}.yml | Get a delivery config manifest
[**GetOnboardingReportUsingGET**](ManagedControllerApi.md#GetOnboardingReportUsingGET) | **Get** /managed/reports/onboarding | Get a report of application onboarding
[**GetResourceStatusUsingGET**](ManagedControllerApi.md#GetResourceStatusUsingGET) | **Get** /managed/resources/{resourceId}/status | Get status of a resource
[**GetResourceUsingGET**](ManagedControllerApi.md#GetResourceUsingGET) | **Get** /managed/resources/{resourceId} | Get a resource
[**GetResourceYamlUsingGET**](ManagedControllerApi.md#GetResourceYamlUsingGET) | **Get** /managed/resources/{resourceId}.yml | Get a resource
[**GraphqlUsingPOST**](ManagedControllerApi.md#GraphqlUsingPOST) | **Post** /managed/graphql | Post a graphql request
[**MarkBadUsingPOST**](ManagedControllerApi.md#MarkBadUsingPOST) | **Post** /managed/application/{application}/mark/bad | Veto an artifact version in an environment
[**MarkGoodUsingPOST**](ManagedControllerApi.md#MarkGoodUsingPOST) | **Post** /managed/application/{application}/mark/good | Delete veto of an artifact version in an environment
[**OverrideVerificationUsingPOST**](ManagedControllerApi.md#OverrideVerificationUsingPOST) | **Post** /managed/{application}/environment/{environment}/verifications | Override the status of a verification
[**PauseApplicationUsingPOST**](ManagedControllerApi.md#PauseApplicationUsingPOST) | **Post** /managed/application/{application}/pause | Pause management of an entire application
[**PauseResourceUsingPOST**](ManagedControllerApi.md#PauseResourceUsingPOST) | **Post** /managed/resources/{resourceId}/pause | Pause management of a resource
[**ProcessNotificationCallbackUsingPOST**](ManagedControllerApi.md#ProcessNotificationCallbackUsingPOST) | **Post** /managed/notifications/callbacks/{source} | processNotificationCallback
[**ResumeApplicationUsingDELETE**](ManagedControllerApi.md#ResumeApplicationUsingDELETE) | **Delete** /managed/application/{application}/pause | Resume management of an entire application
[**ResumeResourceUsingDELETE**](ManagedControllerApi.md#ResumeResourceUsingDELETE) | **Delete** /managed/resources/{resourceId}/pause | Resume management of a resource
[**RetryVerificationUsingPOST**](ManagedControllerApi.md#RetryVerificationUsingPOST) | **Post** /managed/{application}/environment/{environment}/verifications/retry | Retry a verification
[**SchemaUsingGET**](ManagedControllerApi.md#SchemaUsingGET) | **Get** /managed/delivery-configs/schema | Ad-hoc validate and diff a config manifest
[**UpdateConstraintStatusUsingPOST**](ManagedControllerApi.md#UpdateConstraintStatusUsingPOST) | **Post** /managed/application/{application}/environment/{environment}/constraint | Update the status of an environment constraint
[**UpsertManifestUsingPOST**](ManagedControllerApi.md#UpsertManifestUsingPOST) | **Post** /managed/delivery-configs | Create or update a delivery config manifest
[**ValidateManifestUsingPOST**](ManagedControllerApi.md#ValidateManifestUsingPOST) | **Post** /managed/delivery-configs/validate | Validate a delivery config manifest
[**VetoUsingPOST**](ManagedControllerApi.md#VetoUsingPOST) | **Post** /managed/application/{application}/veto | Veto an artifact version in an environment


# **CreatePinUsingPOST**
> CreatePinUsingPOST(ctx, application, pin)
Create a pin for an artifact in an environment

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **pin** | [**EnvironmentArtifactPin**](EnvironmentArtifactPin.md)| pin | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **DeleteManifestByAppUsingDELETE**
> DeliveryConfig DeleteManifestByAppUsingDELETE(ctx, application)
Delete a delivery config manifest for an application

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 

### Return type

[**DeliveryConfig**](DeliveryConfig.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **DeleteManifestUsingDELETE**
> DeliveryConfig DeleteManifestUsingDELETE(ctx, name)
Delete a delivery config manifest

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **name** | **string**| name | 

### Return type

[**DeliveryConfig**](DeliveryConfig.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **DeletePinUsingDELETE**
> DeletePinUsingDELETE(ctx, application, targetEnvironment, optional)
Unpin one or more artifact(s) in an environment. If the `reference` parameter is specified, only the corresponding artifact will be unpinned. If it's omitted, all pinned artifacts in the environment will be unpinned.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **targetEnvironment** | **string**| targetEnvironment | 
 **optional** | ***ManagedControllerApiDeletePinUsingDELETEOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ManagedControllerApiDeletePinUsingDELETEOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------


 **reference** | **optional.String**| reference | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **DeleteVetoUsingDELETE**
> DeleteVetoUsingDELETE(ctx, application, reference, targetEnvironment, version)
Remove veto of an artifact version in an environment

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **reference** | **string**| reference | 
  **targetEnvironment** | **string**| targetEnvironment | 
  **version** | **string**| version | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **DiffManifestUsingPOST**
> interface{} DiffManifestUsingPOST(ctx, manifest)
Ad-hoc validate and diff a config manifest

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **manifest** | [**DeliveryConfig**](DeliveryConfig.md)| manifest | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json, application/x-yaml
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **DiffResourceUsingPOST**
> interface{} DiffResourceUsingPOST(ctx, resource)
Ad-hoc validate and diff a resource

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **resource** | [**Resource**](Resource.md)| resource | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json, application/x-yaml
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ExportResourceUsingGET**
> interface{} ExportResourceUsingGET(ctx, account, cloudProvider, clusterName)
Generates an artifact definition based on the artifact used in a running cluster

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **account** | **string**| account | 
  **cloudProvider** | **string**| cloudProvider | 
  **clusterName** | **string**| clusterName | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ExportResourceUsingGET1**
> Resource ExportResourceUsingGET1(ctx, account, cloudProvider, name, serviceAccount, type_)
Generate a keel resource definition for a deployed cloud resource

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **account** | **string**| account | 
  **cloudProvider** | **string**| cloudProvider | 
  **name** | **string**| name | 
  **serviceAccount** | **string**| serviceAccount | 
  **type_** | **string**| type | 

### Return type

[**Resource**](Resource.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetAdoptionReportUsingGET**
> string GetAdoptionReportUsingGET(ctx, params)
Get a report of Managed Delivery adoption

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **params** | [**interface{}**](.md)| params | 

### Return type

**string**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: text/html

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetApplicationDetailsUsingGET**
> interface{} GetApplicationDetailsUsingGET(ctx, application, optional)
Get managed details about an application

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
 **optional** | ***ManagedControllerApiGetApplicationDetailsUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ManagedControllerApiGetApplicationDetailsUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **entities** | [**optional.Interface of []string**](string.md)| entities | 
 **includeDetails** | **optional.Bool**| includeDetails | [default to false]
 **maxArtifactVersions** | **optional.Int32**| maxArtifactVersions | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetConfigByUsingGET**
> DeliveryConfig GetConfigByUsingGET(ctx, application)
Get the delivery config associated with an application

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 

### Return type

[**DeliveryConfig**](DeliveryConfig.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetConstraintStateUsingGET**
> ConstraintState GetConstraintStateUsingGET(ctx, application, environment, optional)
List up-to {limit} current constraint states for an environment

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **environment** | **string**| environment | 
 **optional** | ***ManagedControllerApiGetConstraintStateUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ManagedControllerApiGetConstraintStateUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------


 **limit** | **optional.String**| limit | [default to 10]

### Return type

[**ConstraintState**](ConstraintState.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetEnvironmentsUsingGET**
> []Mapstringobject GetEnvironmentsUsingGET(ctx, application)
Get current environment details

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 

### Return type

[**[]Mapstringobject**](Map«string,object».md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetManifestArtifactsUsingGET**
> []interface{} GetManifestArtifactsUsingGET(ctx, name)
Get the status of each version of each artifact in each environment

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **name** | **string**| name | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetManifestUsingGET**
> DeliveryConfig GetManifestUsingGET(ctx, name)
Get a delivery config manifest

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **name** | **string**| name | 

### Return type

[**DeliveryConfig**](DeliveryConfig.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetManifestYamlUsingGET**
> DeliveryConfig GetManifestYamlUsingGET(ctx, name)
Get a delivery config manifest

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **name** | **string**| name | 

### Return type

[**DeliveryConfig**](DeliveryConfig.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/x-yaml

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetOnboardingReportUsingGET**
> string GetOnboardingReportUsingGET(ctx, accept, params)
Get a report of application onboarding

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **accept** | **string**| Accept | [default to text/html]
  **params** | [**interface{}**](.md)| params | 

### Return type

**string**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetResourceStatusUsingGET**
> interface{} GetResourceStatusUsingGET(ctx, resourceId)
Get status of a resource

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **resourceId** | **string**| resourceId | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetResourceUsingGET**
> Resource GetResourceUsingGET(ctx, resourceId)
Get a resource

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **resourceId** | **string**| resourceId | 

### Return type

[**Resource**](Resource.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetResourceYamlUsingGET**
> Resource GetResourceYamlUsingGET(ctx, resourceId)
Get a resource

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **resourceId** | **string**| resourceId | 

### Return type

[**Resource**](Resource.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/x-yaml

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GraphqlUsingPOST**
> interface{} GraphqlUsingPOST(ctx, query)
Post a graphql request

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **query** | [**GraphQlRequest**](GraphQlRequest.md)| query | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **MarkBadUsingPOST**
> MarkBadUsingPOST(ctx, application, veto)
Veto an artifact version in an environment

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **veto** | [**EnvironmentArtifactVeto**](EnvironmentArtifactVeto.md)| veto | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **MarkGoodUsingPOST**
> MarkGoodUsingPOST(ctx, application, veto)
Delete veto of an artifact version in an environment

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **veto** | [**EnvironmentArtifactVeto**](EnvironmentArtifactVeto.md)| veto | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **OverrideVerificationUsingPOST**
> OverrideVerificationUsingPOST(ctx, application, environment, payload)
Override the status of a verification

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **environment** | **string**| environment | 
  **payload** | [**OverrideVerificationRequest**](OverrideVerificationRequest.md)| payload | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **PauseApplicationUsingPOST**
> PauseApplicationUsingPOST(ctx, application)
Pause management of an entire application

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **PauseResourceUsingPOST**
> PauseResourceUsingPOST(ctx, resourceId)
Pause management of a resource

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **resourceId** | **string**| resourceId | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ProcessNotificationCallbackUsingPOST**
> string ProcessNotificationCallbackUsingPOST(ctx, source, optional)
processNotificationCallback

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **source** | **string**| source | 
 **optional** | ***ManagedControllerApiProcessNotificationCallbackUsingPOSTOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ManagedControllerApiProcessNotificationCallbackUsingPOSTOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **body** | **optional.String**|  | 
 **headersETag** | **optional.String**|  | 
 **headersAcceptCharset0Registered** | **optional.Bool**|  | 
 **headersAcceptLanguageAsLocales0ISO3Country** | **optional.String**|  | 
 **headersAcceptLanguageAsLocales0ISO3Language** | **optional.String**|  | 
 **headersAcceptLanguageAsLocales0Country** | **optional.String**|  | 
 **headersAcceptLanguageAsLocales0DisplayCountry** | **optional.String**|  | 
 **headersAcceptLanguageAsLocales0DisplayLanguage** | **optional.String**|  | 
 **headersAcceptLanguageAsLocales0DisplayName** | **optional.String**|  | 
 **headersAcceptLanguageAsLocales0DisplayScript** | **optional.String**|  | 
 **headersAcceptLanguageAsLocales0DisplayVariant** | **optional.String**|  | 
 **headersAcceptLanguageAsLocales0Language** | **optional.String**|  | 
 **headersAcceptLanguageAsLocales0Script** | **optional.String**|  | 
 **headersAcceptLanguageAsLocales0UnicodeLocaleAttributes** | [**optional.Interface of []string**](string.md)|  | 
 **headersAcceptLanguageAsLocales0UnicodeLocaleKeys** | [**optional.Interface of []string**](string.md)|  | 
 **headersAcceptLanguageAsLocales0Variant** | **optional.String**|  | 
 **headersAcceptLanguage0Range** | **optional.String**|  | 
 **headersAcceptLanguage0Weight** | **optional.Float64**|  | 
 **headersAcceptPatch0CharsetRegistered** | **optional.Bool**|  | 
 **headersAcceptPatch0Concrete** | **optional.Bool**|  | 
 **headersAcceptPatch0QualityValue** | **optional.Float64**|  | 
 **headersAcceptPatch0Subtype** | **optional.String**|  | 
 **headersAcceptPatch0SubtypeSuffix** | **optional.String**|  | 
 **headersAcceptPatch0Type** | **optional.String**|  | 
 **headersAcceptPatch0WildcardSubtype** | **optional.Bool**|  | 
 **headersAcceptPatch0WildcardType** | **optional.Bool**|  | 
 **headersAccept0CharsetRegistered** | **optional.Bool**|  | 
 **headersAccept0Concrete** | **optional.Bool**|  | 
 **headersAccept0QualityValue** | **optional.Float64**|  | 
 **headersAccept0Subtype** | **optional.String**|  | 
 **headersAccept0SubtypeSuffix** | **optional.String**|  | 
 **headersAccept0Type** | **optional.String**|  | 
 **headersAccept0WildcardSubtype** | **optional.Bool**|  | 
 **headersAccept0WildcardType** | **optional.Bool**|  | 
 **headersAccessControlAllowCredentials** | **optional.Bool**|  | 
 **headersAccessControlAllowHeaders** | [**optional.Interface of []string**](string.md)|  | 
 **headersAccessControlAllowMethods** | [**optional.Interface of []string**](string.md)|  | 
 **headersAccessControlAllowOrigin** | **optional.String**|  | 
 **headersAccessControlExposeHeaders** | [**optional.Interface of []string**](string.md)|  | 
 **headersAccessControlMaxAge** | **optional.Int64**|  | 
 **headersAccessControlRequestHeaders** | [**optional.Interface of []string**](string.md)|  | 
 **headersAccessControlRequestMethod** | **optional.String**|  | 
 **headersAllow** | [**optional.Interface of []string**](string.md)|  | 
 **headersCacheControl** | **optional.String**|  | 
 **headersConnection** | [**optional.Interface of []string**](string.md)|  | 
 **headersContentDispositionAttachment** | **optional.Bool**|  | 
 **headersContentDispositionCharsetRegistered** | **optional.Bool**|  | 
 **headersContentDispositionCreationDate** | **optional.Time**|  | 
 **headersContentDispositionFilename** | **optional.String**|  | 
 **headersContentDispositionFormData** | **optional.Bool**|  | 
 **headersContentDispositionInline** | **optional.Bool**|  | 
 **headersContentDispositionModificationDate** | **optional.Time**|  | 
 **headersContentDispositionName** | **optional.String**|  | 
 **headersContentDispositionReadDate** | **optional.Time**|  | 
 **headersContentDispositionSize** | **optional.Int64**|  | 
 **headersContentDispositionType** | **optional.String**|  | 
 **headersContentLanguageISO3Country** | **optional.String**|  | 
 **headersContentLanguageISO3Language** | **optional.String**|  | 
 **headersContentLanguageCountry** | **optional.String**|  | 
 **headersContentLanguageDisplayCountry** | **optional.String**|  | 
 **headersContentLanguageDisplayLanguage** | **optional.String**|  | 
 **headersContentLanguageDisplayName** | **optional.String**|  | 
 **headersContentLanguageDisplayScript** | **optional.String**|  | 
 **headersContentLanguageDisplayVariant** | **optional.String**|  | 
 **headersContentLanguageLanguage** | **optional.String**|  | 
 **headersContentLanguageScript** | **optional.String**|  | 
 **headersContentLanguageUnicodeLocaleAttributes** | [**optional.Interface of []string**](string.md)|  | 
 **headersContentLanguageUnicodeLocaleKeys** | [**optional.Interface of []string**](string.md)|  | 
 **headersContentLanguageVariant** | **optional.String**|  | 
 **headersContentLength** | **optional.Int64**|  | 
 **headersContentTypeCharsetRegistered** | **optional.Bool**|  | 
 **headersContentTypeConcrete** | **optional.Bool**|  | 
 **headersContentTypeQualityValue** | **optional.Float64**|  | 
 **headersContentTypeSubtype** | **optional.String**|  | 
 **headersContentTypeSubtypeSuffix** | **optional.String**|  | 
 **headersContentTypeType** | **optional.String**|  | 
 **headersContentTypeWildcardSubtype** | **optional.Bool**|  | 
 **headersContentTypeWildcardType** | **optional.Bool**|  | 
 **headersDate** | **optional.Int64**|  | 
 **headersExpires** | **optional.Int64**|  | 
 **headersHostAddressMCGlobal** | **optional.Bool**|  | 
 **headersHostAddressMCLinkLocal** | **optional.Bool**|  | 
 **headersHostAddressMCNodeLocal** | **optional.Bool**|  | 
 **headersHostAddressMCOrgLocal** | **optional.Bool**|  | 
 **headersHostAddressMCSiteLocal** | **optional.Bool**|  | 
 **headersHostAddressAddress** | **optional.String**|  | 
 **headersHostAddressAnyLocalAddress** | **optional.Bool**|  | 
 **headersHostAddressCanonicalHostName** | **optional.String**|  | 
 **headersHostAddressHostAddress** | **optional.String**|  | 
 **headersHostAddressHostName** | **optional.String**|  | 
 **headersHostAddressLinkLocalAddress** | **optional.Bool**|  | 
 **headersHostAddressLoopbackAddress** | **optional.Bool**|  | 
 **headersHostAddressMulticastAddress** | **optional.Bool**|  | 
 **headersHostAddressSiteLocalAddress** | **optional.Bool**|  | 
 **headersHostHostName** | **optional.String**|  | 
 **headersHostHostString** | **optional.String**|  | 
 **headersHostPort** | **optional.Int32**|  | 
 **headersHostUnresolved** | **optional.Bool**|  | 
 **headersIfMatch** | [**optional.Interface of []string**](string.md)|  | 
 **headersIfModifiedSince** | **optional.Int64**|  | 
 **headersIfNoneMatch** | [**optional.Interface of []string**](string.md)|  | 
 **headersIfUnmodifiedSince** | **optional.Int64**|  | 
 **headersLastModified** | **optional.Int64**|  | 
 **headersLocationAbsolute** | **optional.Bool**|  | 
 **headersLocationAuthority** | **optional.String**|  | 
 **headersLocationFragment** | **optional.String**|  | 
 **headersLocationHost** | **optional.String**|  | 
 **headersLocationOpaque** | **optional.Bool**|  | 
 **headersLocationPath** | **optional.String**|  | 
 **headersLocationPort** | **optional.Int32**|  | 
 **headersLocationQuery** | **optional.String**|  | 
 **headersLocationRawAuthority** | **optional.String**|  | 
 **headersLocationRawFragment** | **optional.String**|  | 
 **headersLocationRawPath** | **optional.String**|  | 
 **headersLocationRawQuery** | **optional.String**|  | 
 **headersLocationRawSchemeSpecificPart** | **optional.String**|  | 
 **headersLocationRawUserInfo** | **optional.String**|  | 
 **headersLocationScheme** | **optional.String**|  | 
 **headersLocationSchemeSpecificPart** | **optional.String**|  | 
 **headersLocationUserInfo** | **optional.String**|  | 
 **headersOrigin** | **optional.String**|  | 
 **headersPragma** | **optional.String**|  | 
 **headersUpgrade** | **optional.String**|  | 
 **headersVary** | [**optional.Interface of []string**](string.md)|  | 
 **method** | **optional.String**|  | 
 **type_** | [**optional.Interface of Object**](.md)|  | 
 **urlAbsolute** | **optional.Bool**|  | 
 **urlAuthority** | **optional.String**|  | 
 **urlFragment** | **optional.String**|  | 
 **urlHost** | **optional.String**|  | 
 **urlOpaque** | **optional.Bool**|  | 
 **urlPath** | **optional.String**|  | 
 **urlPort** | **optional.Int32**|  | 
 **urlQuery** | **optional.String**|  | 
 **urlRawAuthority** | **optional.String**|  | 
 **urlRawFragment** | **optional.String**|  | 
 **urlRawPath** | **optional.String**|  | 
 **urlRawQuery** | **optional.String**|  | 
 **urlRawSchemeSpecificPart** | **optional.String**|  | 
 **urlRawUserInfo** | **optional.String**|  | 
 **urlScheme** | **optional.String**|  | 
 **urlSchemeSpecificPart** | **optional.String**|  | 
 **urlUserInfo** | **optional.String**|  | 

### Return type

**string**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/x-www-form-urlencoded
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ResumeApplicationUsingDELETE**
> ResumeApplicationUsingDELETE(ctx, application)
Resume management of an entire application

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ResumeResourceUsingDELETE**
> ResumeResourceUsingDELETE(ctx, resourceId)
Resume management of a resource

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **resourceId** | **string**| resourceId | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **RetryVerificationUsingPOST**
> RetryVerificationUsingPOST(ctx, application, environment, payload, verificationId)
Retry a verification

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **environment** | **string**| environment | 
  **payload** | [**RetryVerificationRequest**](RetryVerificationRequest.md)| payload | 
  **verificationId** | **string**| verificationId | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **SchemaUsingGET**
> interface{} SchemaUsingGET(ctx, )
Ad-hoc validate and diff a config manifest

### Required Parameters
This endpoint does not need any parameter.

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json, application/x-yaml

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **UpdateConstraintStatusUsingPOST**
> UpdateConstraintStatusUsingPOST(ctx, application, environment, status)
Update the status of an environment constraint

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **environment** | **string**| environment | 
  **status** | [**ConstraintStatus**](ConstraintStatus.md)| status | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **UpsertManifestUsingPOST**
> DeliveryConfig UpsertManifestUsingPOST(ctx, manifest)
Create or update a delivery config manifest

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **manifest** | [**DeliveryConfig**](DeliveryConfig.md)| manifest | 

### Return type

[**DeliveryConfig**](DeliveryConfig.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json, application/x-yaml
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ValidateManifestUsingPOST**
> interface{} ValidateManifestUsingPOST(ctx, manifest)
Validate a delivery config manifest

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **manifest** | [**DeliveryConfig**](DeliveryConfig.md)| manifest | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json, application/x-yaml
 - **Accept**: application/json, application/x-yaml

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **VetoUsingPOST**
> VetoUsingPOST(ctx, application, veto)
Veto an artifact version in an environment

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **veto** | [**EnvironmentArtifactVeto**](EnvironmentArtifactVeto.md)| veto | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

