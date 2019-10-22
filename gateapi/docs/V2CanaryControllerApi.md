# \V2CanaryControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetCanaryResultUsingGET**](V2CanaryControllerApi.md#GetCanaryResultUsingGET) | **Get** /v2/canaries/canary/{canaryConfigId}/{canaryExecutionId} | (DEPRECATED) Retrieve a canary result
[**GetCanaryResultUsingGET1**](V2CanaryControllerApi.md#GetCanaryResultUsingGET1) | **Get** /v2/canaries/canary/{canaryExecutionId} | Retrieve a canary result
[**GetCanaryResultsByApplicationUsingGET**](V2CanaryControllerApi.md#GetCanaryResultsByApplicationUsingGET) | **Get** /v2/canaries/{application}/executions | Retrieve a list of an application&#39;s canary results
[**GetMetricSetPairListUsingGET**](V2CanaryControllerApi.md#GetMetricSetPairListUsingGET) | **Get** /v2/canaries/metricSetPairList/{metricSetPairListId} | Retrieve a metric set pair list
[**InitiateCanaryUsingPOST**](V2CanaryControllerApi.md#InitiateCanaryUsingPOST) | **Post** /v2/canaries/canary/{canaryConfigId} | Start a canary execution
[**InitiateCanaryWithConfigUsingPOST**](V2CanaryControllerApi.md#InitiateCanaryWithConfigUsingPOST) | **Post** /v2/canaries/canary | Start a canary execution with the supplied canary config
[**ListCredentialsUsingGET**](V2CanaryControllerApi.md#ListCredentialsUsingGET) | **Get** /v2/canaries/credentials | Retrieve a list of configured Kayenta accounts
[**ListJudgesUsingGET**](V2CanaryControllerApi.md#ListJudgesUsingGET) | **Get** /v2/canaries/judges | Retrieve a list of all configured canary judges
[**ListMetricsServiceMetadataUsingGET**](V2CanaryControllerApi.md#ListMetricsServiceMetadataUsingGET) | **Get** /v2/canaries/metadata/metricsService | Retrieve a list of descriptors for use in populating the canary config ui


# **GetCanaryResultUsingGET**
> interface{} GetCanaryResultUsingGET(ctx, canaryConfigId, canaryExecutionId, optional)
(DEPRECATED) Retrieve a canary result

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **canaryConfigId** | **string**| canaryConfigId | 
  **canaryExecutionId** | **string**| canaryExecutionId | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **canaryConfigId** | **string**| canaryConfigId | 
 **canaryExecutionId** | **string**| canaryExecutionId | 
 **storageAccountName** | **string**| storageAccountName | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetCanaryResultUsingGET1**
> interface{} GetCanaryResultUsingGET1(ctx, canaryExecutionId, optional)
Retrieve a canary result

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **canaryExecutionId** | **string**| canaryExecutionId | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **canaryExecutionId** | **string**| canaryExecutionId | 
 **storageAccountName** | **string**| storageAccountName | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetCanaryResultsByApplicationUsingGET**
> []interface{} GetCanaryResultsByApplicationUsingGET(ctx, application, limit, optional)
Retrieve a list of an application's canary results

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **application** | **string**| application | 
  **limit** | **int32**| limit | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **application** | **string**| application | 
 **limit** | **int32**| limit | 
 **statuses** | **string**| Comma-separated list of statuses, e.g.: RUNNING, SUCCEEDED, TERMINAL | 
 **storageAccountName** | **string**| storageAccountName | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetMetricSetPairListUsingGET**
> []interface{} GetMetricSetPairListUsingGET(ctx, metricSetPairListId, optional)
Retrieve a metric set pair list

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **metricSetPairListId** | **string**| metricSetPairListId | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **metricSetPairListId** | **string**| metricSetPairListId | 
 **storageAccountName** | **string**| storageAccountName | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **InitiateCanaryUsingPOST**
> interface{} InitiateCanaryUsingPOST(ctx, canaryConfigId, executionRequest, optional)
Start a canary execution

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **canaryConfigId** | **string**| canaryConfigId | 
  **executionRequest** | [**interface{}**](interface{}.md)| executionRequest | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **canaryConfigId** | **string**| canaryConfigId | 
 **executionRequest** | [**interface{}**](interface{}.md)| executionRequest | 
 **application** | **string**| application | 
 **configurationAccountName** | **string**| configurationAccountName | 
 **metricsAccountName** | **string**| metricsAccountName | 
 **parentPipelineExecutionId** | **string**| parentPipelineExecutionId | 
 **storageAccountName** | **string**| storageAccountName | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **InitiateCanaryWithConfigUsingPOST**
> interface{} InitiateCanaryWithConfigUsingPOST(ctx, adhocExecutionRequest, optional)
Start a canary execution with the supplied canary config

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **adhocExecutionRequest** | [**interface{}**](interface{}.md)| adhocExecutionRequest | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **adhocExecutionRequest** | [**interface{}**](interface{}.md)| adhocExecutionRequest | 
 **application** | **string**| application | 
 **metricsAccountName** | **string**| metricsAccountName | 
 **parentPipelineExecutionId** | **string**| parentPipelineExecutionId | 
 **storageAccountName** | **string**| storageAccountName | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ListCredentialsUsingGET**
> []interface{} ListCredentialsUsingGET(ctx, )
Retrieve a list of configured Kayenta accounts

### Required Parameters
This endpoint does not need any parameter.

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ListJudgesUsingGET**
> []interface{} ListJudgesUsingGET(ctx, )
Retrieve a list of all configured canary judges

### Required Parameters
This endpoint does not need any parameter.

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ListMetricsServiceMetadataUsingGET**
> []interface{} ListMetricsServiceMetadataUsingGET(ctx, optional)
Retrieve a list of descriptors for use in populating the canary config ui

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **filter** | **string**| filter | 
 **metricsAccountName** | **string**| metricsAccountName | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

