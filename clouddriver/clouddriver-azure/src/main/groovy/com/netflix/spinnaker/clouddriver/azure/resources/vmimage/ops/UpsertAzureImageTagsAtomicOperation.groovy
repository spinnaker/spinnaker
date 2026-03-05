package com.netflix.spinnaker.clouddriver.azure.resources.vmimage.ops

import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.ops.description.UpsertAzureImageTagsDescription
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.util.logging.Slf4j

@Slf4j
class UpsertAzureImageTagsAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_IMAGE_TAGS"

  private final UpsertAzureImageTagsDescription description

  UpsertAzureImageTagsAtomicOperation(UpsertAzureImageTagsDescription description) {
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    def task = getTask()
    def descriptor = "${description.accountName}/${description.imageName}"

    task.updateStatus(BASE_PHASE, "Initializing Upsert Image Tags operation for ${descriptor}...")

    try {
      if (description.isGalleryImage) {
        updateGalleryImageTags()
      } else if (description.isCustomImage) {
        updateManagedImageTags()
      } else {
        task.updateStatus(BASE_PHASE, "Marketplace images cannot be tagged in Azure")
        log.warn("Attempted to tag marketplace image ${descriptor} - marketplace images cannot be tagged")
      }
    } catch (Exception e) {
      task.updateStatus(BASE_PHASE, "Failed to update image tags for ${descriptor}: ${e.message}")
      task.fail()
      throw e
    }

    null
  }

  private void updateGalleryImageTags() {
    def task = getTask()
    def imageIdentifier = description.imageId ?: description.imageName

    task.updateStatus(BASE_PHASE, "Updating tags for gallery image ${imageIdentifier}...")

    def resourceId = description.imageId
    if (!resourceId) {
      task.updateStatus(BASE_PHASE, "No image ID provided for gallery image tagging")
      task.fail()
      return
    }

    def success = description.credentials.computeClient.updateCustomImageTags(
        resourceId,
        description.imageName,
        description.resourceGroupName ?: description.appName,
        description.tags
    )

    if (success) {
      task.updateStatus(BASE_PHASE, "Successfully updated tags for gallery image ${imageIdentifier}")
      log.info("Updated tags for gallery image ${imageIdentifier}: ${description.tags}")
    } else {
      task.updateStatus(BASE_PHASE, "Failed to update tags for gallery image ${imageIdentifier}")
      task.fail()
    }
  }

  private void updateManagedImageTags() {
    def task = getTask()
    def resourceGroup = description.resourceGroupName ?: description.appName
    def imageIdentifier = description.imageName ?: description.imageId

    task.updateStatus(BASE_PHASE, "Updating tags for image ${imageIdentifier}...")

    // Use the new method in AzureComputeClient
    def success = description.credentials.computeClient.updateCustomImageTags(
        description.imageId,
        description.imageName,
        resourceGroup,
        description.tags
    )

    if (success) {
      task.updateStatus(BASE_PHASE, "Successfully updated tags for image ${imageIdentifier}")
      log.info("Updated tags for image ${imageIdentifier}: ${description.tags}")
    } else {
      task.updateStatus(BASE_PHASE, "Failed to update tags for image ${imageIdentifier}")
      task.fail()
    }
  }
}
