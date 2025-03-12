<#include "../manualJudgment/variables-email.ftl">
${stageDescription} <i>was judged to <b>continue</b></i> by ${notification.additionalContext.judgedBy}.

<#if (notification.additionalContext.message)??>
${markdownToHtml.convert(notification.additionalContext.message)}
</#if>

<#if (notification.additionalContext.judgmentInput)??>
Judgment '${htmlToText.convert(notification.additionalContext.judgmentInput)}' was selected.
</#if>
