# \ReorderPipelinesControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**ReorderPipelinesUsingPOST**](ReorderPipelinesControllerApi.md#ReorderPipelinesUsingPOST) | **Post** /actions/pipelines/reorder | Re-order pipelines


# **ReorderPipelinesUsingPOST**
> interface{} ReorderPipelinesUsingPOST(ctx, reorderPipelinesCommand)
Re-order pipelines

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **reorderPipelinesCommand** | [**ReorderPipelinesCommand**](ReorderPipelinesCommand.md)| reorderPipelinesCommand | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

