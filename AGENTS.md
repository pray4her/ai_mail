# Repository Guidelines

## Project Structure & Module Organization

This repository is a Java 17 Spring Boot backend for AI-assisted mail replies. The Maven project lives in `ai_mail_src/`; use `ai_mail_src/pom.xml` as the dependency and version source of truth. Application code is under `ai_mail_src/src/main/java/com/github/mail`:

- `controller/`: REST endpoints under `/api/*`, including AI preview and streaming endpoints.
- `service/`: business logic for AI generation, RAG, mail fetching/sending, scheduling, rules, persistence, and users.
- `service/ai/`: the unified AI facade built on Spring AI and Langfuse. Keep provider routing, prompt preparation, tracing, evaluation, and sync/stream generation here instead of scattering model logic across business services.
- `repo/`: MyBatis-Plus domain, DTO, mapper, and DAO classes.
- `client/`: vendor SDK clients that still remain necessary, such as Bailian knowledge-base retrieval, MinIO, or other non-Spring-AI integrations. Do not add new vendor-specific chat or embedding clients when Spring AI can cover the use case.
- `config/`, `config/properties/`, `model/config/`, `utils/`: Spring configuration, typed properties, config models, and helpers.

Tests live in `ai_mail_src/src/test/java/com/github/mail`. Primary runtime configuration is `ai_mail_src/src/main/resources/application.yml`, with secrets and deployment-specific values supplied via environment variables. MySQL schema changes are managed by Flyway migrations in `ai_mail_src/src/main/resources/db/migration`; `mail.sql`, `schema.sql`, and `db_upgrade_history_sync.sql` are historical references only.

## AI Architecture Expectations

- Chat and embedding calls must go through the Spring AI-based `service/ai/` layer.
- Use `ChatProviderRegistry` to resolve configured OpenAI-compatible providers; do not hardcode provider URLs, model names, or API keys in service logic.
- Prompt assembly should be handled by `LangfusePromptService`, with a safe local fallback only when Langfuse is disabled or unavailable.
- RAG routing should continue to support both `local` Elasticsearch hybrid search and `bailian` retrieval providers.
- New AI-facing APIs should prefer the shared `AiGenerationService` for both synchronous and streaming behavior.

## Build, Test, and Development Commands

- `mvn -f ai_mail_src/pom.xml spring-boot:run`: start the local service.
- `mvn -f ai_mail_src/pom.xml flyway:validate flyway:migrate`: validate and apply explicit MySQL migrations.
- `mvn -f ai_mail_src/pom.xml test`: run all JUnit/Spring Boot tests.
- `mvn -f ai_mail_src/pom.xml clean test`: run a clean full test pass before finishing larger refactors.
- `mvn -f ai_mail_src/pom.xml "-Dtest=com.github.mail.service.ai.SpringAiGenerationServiceTest" test`: run one focused test class.
- `mvn -f ai_mail_src/pom.xml -DskipTests package`: build the jar under `ai_mail_src/target`.

Do not edit generated files in `ai_mail_src/target`.

## Coding Style & Naming Conventions

Use standard Java formatting with 4-space indentation. Package names are lowercase (`com.github.mail.service.ai`), classes are PascalCase (`SpringAiGenerationService`), methods and variables are camelCase (`preparePrompt`), and constants are UPPER_SNAKE_CASE. Prefer constructor injection with Lombok `@RequiredArgsConstructor` and `final` fields.

Prefer:
- typed `@ConfigurationProperties` over ad hoc config readers
- small DTOs/records for AI request-response boundaries
- parameterized logs with `@Slf4j`

Never log secrets, tokens, API keys, passwords, or full sensitive mail bodies.

## Testing Guidelines

Use JUnit 5 and Spring Boot Test. Name tests `*Test.java` or `*Tests.java`. Prefer focused unit tests for:

- provider routing
- prompt preparation and fallback behavior
- sync/stream AI generation orchestration
- scheduler orchestration
- RAG routing and embedding integration

When testing OpenAI-compatible behavior, prefer mocks or HTTP mock infrastructure over real external model calls. Avoid tests that require live mail servers, live model endpoints, or a live Elasticsearch cluster unless the change is explicitly an integration task.

## Commit & Pull Request Guidelines

The current `main` branch has no stable historical convention, so use short imperative subjects such as `Add AI preview streaming endpoint` or `Refactor RAG embedding pipeline`. Pull requests should describe:

- behavior changes
- tests run
- config or environment variable changes
- schema or operational impacts

Include API examples only when they help clarify frontend-facing behavior.

## Security & Agent Notes

Keep JWT secrets, database passwords, mail credentials, Langfuse credentials, and AI API keys out of commits; prefer environment variables. Public endpoints must be explicitly allowed in `WebConfig`; all others should require `Authorization: Bearer <JWT>`.

For this repository, do not ask the user to repeat Langfuse credential locations in every chat. The canonical credential source is the repository root `.env` file (loaded via `spring.config.import`), using environment variable keys `APP_LANGFUSE_URL`, `APP_LANGFUSE_PUBLIC_KEY`, `APP_LANGFUSE_SECRET_KEY`, plus prompt/trace metadata keys `APP_LANGFUSE_PROMPT_NAME`, `APP_LANGFUSE_PROMPT_LABEL`, `APP_LANGFUSE_PROMPT_VERSION`, `APP_LANGFUSE_TRACE_NAME`, and `APP_LANGFUSE_ENVIRONMENT`. Reference only key names and file path; never request or echo plaintext credential values in chat.

For library, SDK, CLI, framework, or cloud-service questions, fetch current docs through Context7 or the available docs tooling before answering from memory. In this repository, prefer `rg`/Cursor search tools for code search over manual shell grep-style workflows.

## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues; external PRs are not a triage request surface. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the default triage label vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, and `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Use a single-context domain docs layout. See `docs/agents/domain.md`.
