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
[**GetApiDocsUsingGET**](ManagedControllerApi.md#GetApiDocsUsingGET) | **Get** /managed/api-docs | getApiDocs
[**GetApplicationDetailsUsingGET**](ManagedControllerApi.md#GetApplicationDetailsUsingGET) | **Get** /managed/application/{application} | Get managed details about an application
[**GetConfigByUsingGET**](ManagedControllerApi.md#GetConfigByUsingGET) | **Get** /managed/application/{application}/config | Get the delivery config associated with an application
[**GetConstraintStateUsingGET**](ManagedControllerApi.md#GetConstraintStateUsingGET) | **Get** /managed/application/{application}/environment/{environment}/constraints | List up-to {limit} current constraint states for an environment
[**GetManifestArtifactsUsingGET**](ManagedControllerApi.md#GetManifestArtifactsUsingGET) | **Get** /managed/delivery-configs/{name}/artifacts | Get the status of each version of each artifact in each environment
[**GetManifestUsingGET**](ManagedControllerApi.md#GetManifestUsingGET) | **Get** /managed/delivery-configs/{name} | Get a delivery config manifest
[**GetResourceStatusUsingGET**](ManagedControllerApi.md#GetResourceStatusUsingGET) | **Get** /managed/resources/{resourceId}/status | Get status of a resource
[**GetResourceUsingGET**](ManagedControllerApi.md#GetResourceUsingGET) | **Get** /managed/resources/{resourceId} | Get a resource
[**PauseApplicationUsingPOST**](ManagedControllerApi.md#PauseApplicationUsingPOST) | **Post** /managed/application/{application}/pause | Pause management of an entire application
[**PauseResourceUsingPOST**](ManagedControllerApi.md#PauseResourceUsingPOST) | **Post** /managed/resources/{resourceId}/pause | Pause management of a resource
[**ResumeApplicationUsingDELETE**](ManagedControllerApi.md#ResumeApplicationUsingDELETE) | **Delete** /managed/application/{application}/pause | Resume management of an entire application
[**ResumeResourceUsingDELETE**](ManagedControllerApi.md#ResumeResourceUsingDELETE) | **Delete** /managed/resources/{resourceId}/pause | Resume management of a resource
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

# **GetApiDocsUsingGET**
> interface{} GetApiDocsUsingGET(ctx, )
getApiDocs

### Required Parameters
This endpoint does not need any parameter.

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

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

