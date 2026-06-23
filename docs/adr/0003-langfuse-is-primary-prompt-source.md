# Langfuse is primary prompt source

AI Mail treats Langfuse as the primary source for reply-generation prompts, with a local fallback only for degraded operation. This lets prompt changes, labels, versions, traces, and evaluations be managed outside application deployments while preserving service continuity when Langfuse is unavailable.
