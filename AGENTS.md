# Repository Guidelines

## Project Structure & Module Organization

This is a Java 17 Spring Boot backend for AI-assisted mail replies. The Maven project lives in `ai_mail_src/`; use `ai_mail_src/pom.xml` as the dependency and version source of truth. Application code is under `ai_mail_src/src/main/java/com/github/mail`:

- `controller/`: REST endpoints, mostly under `/api/*`.
- `service/`: business logic for RAG, mail fetching, scheduling, rules, config, and users.
- `repo/`: MyBatis-Plus domain, DTO, mapper, and DAO classes.
- `client/`: external clients for DeepSeek, Aliyun embedding, MinIO, and related APIs.
- `config/`, `model/config/`, `utils/`: Spring configuration, property models, and helpers.

Tests are in `ai_mail_src/src/test/java/com/github/mail`. Runtime config is split between `ai_mail_src/src/main/resources/application.yml` and `ai_mail_src/config/config.json`. SQL references are in `mail.sql` and `schema.sql`.

## Build, Test, and Development Commands

- `mvn -f ai_mail_src/pom.xml spring-boot:run`: start the local service.
- `mvn -f ai_mail_src/pom.xml test`: run all JUnit/Spring Boot tests.
- `mvn -f ai_mail_src/pom.xml test -Dtest=RagIntegrationTest`: run one test class.
- `mvn -f ai_mail_src/pom.xml -DskipTests package`: build the jar under `ai_mail_src/target`.

Do not edit generated files in `ai_mail_src/target`.

## Coding Style & Naming Conventions

Use standard Java formatting with 4-space indentation. Package names are lowercase (`com.github.mail.service`), classes are PascalCase (`PromptBuilder`), methods and variables are camelCase (`buildPrompt`), and constants are UPPER_SNAKE_CASE. Prefer constructor injection with Lombok `@RequiredArgsConstructor` and `final` fields. Use `@Slf4j` and parameterized logs; never log secrets, tokens, API keys, passwords, or full mail bodies.

## Testing Guidelines

Use JUnit 5 and Spring Boot Test. Name tests `*Test.java` or `*Tests.java`, following existing examples such as `AccountAuthServiceImplTest` and `MailApplicationTests`. Add focused unit tests for service logic and integration tests when touching RAG, Elasticsearch, MinIO, MySQL, scheduling, or authentication flows.

## Commit & Pull Request Guidelines

The current `main` branch has no commit history, so no repository-specific convention is established. Use short imperative subjects such as `Add account login validation` or `Fix RAG score filtering`. Pull requests should describe behavior changes, tests run, config or schema changes, and related issues or specs. Include API examples only when they clarify frontend-facing changes.

## Security & Agent Notes

Keep JWT secrets, database passwords, mail credentials, and AI API keys out of commits; prefer environment variables or external config. Public endpoints must be explicitly allowed in `WebConfig`; all others should require `Authorization: Bearer <JWT>`. For library, SDK, CLI, framework, or cloud-service questions, fetch current docs with `npx ctx7@latest library ...` then `npx ctx7@latest docs ...`. In this environment, use native PowerShell search commands instead of `rg`.
