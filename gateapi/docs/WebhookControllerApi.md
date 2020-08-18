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

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **WebhooksUsingPOST**
> interface{} WebhooksUsingPOST(ctx, source, type_, optional)
Endpoint for posting webhooks to Spinnaker's webhook service

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **source** | **string**| source | 
  **type_** | **string**| type | 
 **optional** | ***WebhookControllerApiWebhooksUsingPOSTOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a WebhookControllerApiWebhooksUsingPOSTOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------


 **xEventKey** | **optional.String**| X-Event-Key | 
 **xHubSignature** | **optional.String**| X-Hub-Signature | 
 **event** | [**optional.Interface of interface{}**](interface{}.md)| event | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

