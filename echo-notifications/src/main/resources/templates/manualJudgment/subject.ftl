<#if (notification.additionalContext.execution.name)??>
  <#if (notification.additionalContext.execution.trigger.buildInfo.number)??>
Stage ${notification.additionalContext.stageName} for ${notification.source.application}'s ${notification.additionalContext.execution.name} pipeline build ${notification.additionalContext.execution.trigger.buildInfo.number} is awaiting manual judgment
  <#else>
Stage ${notification.additionalContext.stageName} for ${notification.source.application}'s ${notification.additionalContext.execution.name} pipeline is awaiting manual judgment
  </#if>
<#else>
Stage ${notification.additionalContext.stageName} for ${notification.source.application} is awaiting manual judgment
</#if>
