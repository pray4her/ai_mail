# Route AI calls through Spring AI layer

AI Mail routes chat generation and embeddings through the Spring AI based AI layer. Business services depend on shared generation, embedding, prompt, provider, and tracing abstractions instead of calling model-provider SDKs directly, so provider routing and observability stay centralized.
