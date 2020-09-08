<#if (notification.additionalContext.execution.name)??>
  <#if (notification.additionalContext.execution.trigger.buildInfo.number)??>
Stage ${notification.additionalContext.stageName} for ${notification.source.application}'s ${notification.additionalContext.execution.name} pipeline build ${notification.additionalContext.execution.trigger.buildInfo.number} was judged to continue by ${notification.additionalContext.judgedBy}.
  <#else>
Stage ${notification.additionalContext.stageName} for ${notification.source.application}'s ${notification.additionalContext.execution.name} pipeline was judged to continue by ${notification.additionalContext.judgedBy}.
  </#if>
<#else>
Stage ${notification.additionalContext.stageName} for ${notification.source.application} was judged to continue by ${notification.additionalContext.judgedBy}.
</#if>

<#if (notification.additionalContext.message)??>
${htmlToText.convert(notification.additionalContext.message)}
</#if>
<#if (notification.additionalContext.judgmentInput)??>
Judgment '${htmlToText.convert(notification.additionalContext.judgmentInput)}' was selected.
</#if>

For more details, please click the View Execution button below.
