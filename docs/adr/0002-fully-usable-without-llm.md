# The service is fully usable without the LLM

Filtering, browsing, name landing pages, and shortlisting must always work when the LLM is slow, queued, or down — only the Interview conversation waits. This is why filter state is visible and hand-editable rather than living inside the chat. We chose this because the LLM is self-hosted on limited hardware (evaluated on the maintainer's own GPU first, behind an OpenAI-compatible endpoint so it can move to rented GPUs as a config change), so LLM capacity must never cap the availability of an ad-funded public page.
