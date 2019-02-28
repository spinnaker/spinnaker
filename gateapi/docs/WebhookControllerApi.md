# \WebhookControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**PreconfiguredWebhooksUsingGET**](WebhookControllerApi.md#PreconfiguredWebhooksUsingGET) | **Get** /webhooks/preconfigured | Retrieve a list of preconfigured webhooks in Orca
[**WebhooksUsingPOST**](WebhookControllerApi.md#WebhooksUsingPOST) | **Post** /webhooks/{type}/{source} | Endpoint for posting webhooks to Spinnaker&#39;s webhook service


# **PreconfiguredWebhooksUsingGET**
> []interface{} PreconfiguredWebhooksUsingGET(ctx, )
Retrieve a list of preconfigured webhooks in Orca

### Required Parameters
This endpoint does not need any parameter.

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **WebhooksUsingPOST**
> interface{} WebhooksUsingPOST(ctx, type_, source, optional)
Endpoint for posting webhooks to Spinnaker's webhook service

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **type_** | **string**| type | 
  **source** | **string**| source | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **type_** | **string**| type | 
 **source** | **string**| source | 
 **event** | [**interface{}**](interface{}.md)| event | 
 **xHubSignature** | **string**| X-Hub-Signature | 
 **xEventKey** | **string**| X-Event-Key | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

