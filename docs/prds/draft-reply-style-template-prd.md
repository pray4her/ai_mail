# PRD: Controlled styled draft replies

Suggested issue title: Add controlled HTML styling for draft replies

Suggested label: `ready-for-agent`

## Problem Statement

Operators currently receive AI-generated 草稿回复 as plain text drafts. This keeps behavior simple, but it prevents the system from applying consistent business email presentation such as font, size, color, spacing, header, footer, text signature, logo, and CTA link styling.

The system needs a controlled way to improve 草稿回复 presentation in email clients without letting AI output raw HTML, without exposing arbitrary CSS, and without losing the current plain-text fallback behavior.

## Solution

Add controlled 草稿回复样式 for automatically generated 草稿回复. AI generation remains plain text. The backend renders that text into a safe, single-column HTML email template with inline CSS and saves the draft as an email with both plain-text and HTML alternatives.

The first version uses global backend configuration only. It supports base typography and color rules, optional header, optional HTTPS logo with alt text, optional CTA link button, optional text signature, and optional footer. It does not add database-backed template management or a management UI.

## User Stories

1. As an operator, I want automatically generated 草稿回复 to have readable default typography, so that drafts are easier to review before sending.
2. As an operator, I want 草稿回复 to keep a plain-text fallback, so that drafts remain readable in clients that do not render HTML well.
3. As an operator, I want styled 草稿回复 to preserve the AI-generated message content, so that presentation does not change the reply's meaning.
4. As an operator, I want paragraph spacing to be consistent, so that generated replies do not appear cramped in email clients.
5. As an operator, I want line breaks in the AI-generated text to be reflected in HTML, so that lists and short paragraphs remain readable.
6. As an operator, I want ordinary safe URLs in the generated body to become links, so that recipients can open referenced resources easily.
7. As an operator, I want unsafe or ambiguous links to remain plain text, so that drafts do not create misleading or dangerous links.
8. As an operator, I want an optional CTA link button, so that common next actions can be emphasized when configured.
9. As an operator, I want the CTA to come from system configuration, so that AI cannot invent structured calls to action.
10. As an operator, I want an optional text signature, so that draft replies can include a consistent sender identity.
11. As an operator, I want an optional page footer, so that standard business notes can be appended consistently.
12. As an operator, I want an optional page header, so that styled drafts can carry a company name or logo.
13. As an operator, I want logo images to require alt text, so that drafts remain understandable when images are blocked.
14. As an operator, I want the logo to use HTTPS only, so that drafts avoid insecure image loading.
15. As a system administrator, I want style settings controlled through backend configuration, so that the first version can be deployed without new database tables or UI.
16. As a system administrator, I want invalid style configuration to fail startup, so that bad outbound presentation is caught early.
17. As a system administrator, I want a configuration switch to disable styled HTML, so that I can return to the current plain-text behavior quickly.
18. As a developer, I want rendering logic separated from mail transport logic, so that text-to-HTML behavior can be tested without IMAP.
19. As a developer, I want mail-saving behavior to keep its current public interface, so that the scheduler does not need to know about styling.
20. As a developer, I want processing records to keep the AI source content, so that stored generation output is not mixed with rendered template output.
21. As a QA tester, I want automated tests that do not require real IMAP, so that the default test suite remains reliable.
22. As a QA tester, I want an optional real-IMAP smoke test, so that rendered drafts can be checked in an actual mailbox.
23. As a future maintainer, I want the first version to avoid arbitrary HTML and CSS editors, so that the feature does not become an unbounded email template system.

## Implementation Decisions

- AI-generated 草稿回复 remains plain text.
- The backend owns all HTML generation and inline CSS.
- Styled drafts are saved with both plain-text and HTML alternatives.
- A style-enabled configuration path saves styled drafts; a disabled configuration path preserves the current plain-text-only behavior.
- Global configuration is used for the first version.
- No database schema changes are included.
- No management UI is included.
- The mail-saving interface remains unchanged; styling is applied inside the mail-saving implementation.
- A dedicated renderer converts source text and configuration into final plain-text and HTML outputs.
- The renderer returns plain data and does not create mail transport objects.
- The final plain-text part may append configured CTA, text signature, and footer text.
- The stored processing record keeps the AI source text rather than the rendered template result.
- Base styling supports controlled font family, font size, text color, muted text color, link color, background color, container background color, line height, paragraph spacing, max width, and content padding.
- Font family is selected from a fixed enum: system, Microsoft YaHei oriented CJK stack, serif, or monospace.
- Colors use six-digit HEX values only.
- Numeric style values use strict allowed ranges.
- Header configuration requires either company name or enabled logo when the header is enabled.
- Logo configuration supports HTTPS image URLs only and requires alt text.
- Ordinary body links are auto-linked only when they include `http`, `https`, or `mailto` protocols.
- Bare domains are not auto-linked.
- URLs are not rewritten and do not receive tracking parameters.
- CTA configuration allows `http`, `https`, or `mailto` protocols, with a short label and controlled colors.
- CTA is rendered as a table-based link button rather than a real button.
- HTML layout uses a single-column, table-based email structure with inline CSS.
- External CSS, JavaScript, forms, complex animation, Flex/Grid, raw webpage HTML, CID images, image signatures, arbitrary CSS editing, arbitrary HTML editing, and AI-generated structured CTA are out of scope.

## Testing Decisions

- Default automated tests must not require a real IMAP server.
- Renderer tests should cover behavior visible in the rendered outputs: HTML escaping, paragraph splitting, single-newline line breaks, safe URL linking, unsafe URL escaping, CTA rendering, text signature rendering, footer rendering, header/logo rendering, and final plain-text composition.
- Configuration tests should cover valid defaults, invalid numeric ranges, invalid color formats, invalid protocols, missing required fields for enabled components, and disabled components with empty fields.
- Mail-saving tests should verify externally observable MIME behavior where practical: plain-text-only output when styling is disabled and alternative plain-text plus HTML output when styling is enabled.
- If MIME construction becomes hard to test in place, a small factory can be extracted later, but this is not required for the first implementation.
- A real-IMAP smoke test may be added as an explicitly enabled integration test. It should save a draft to a dedicated test folder with a smoke-test subject and should not automatically delete the draft, so a human can inspect rendering in the mailbox.

## Out of Scope

- Per-受管邮箱 style overrides.
- Database-backed template storage.
- Management UI for editing templates.
- Arbitrary CSS editor.
- Arbitrary HTML editor.
- Passing raw webpage HTML through to outgoing drafts.
- AI-generated HTML as final email content.
- AI-generated structured CTA.
- CID embedded images.
- Image signatures.
- Multi-column layout in the first version.
- Complex responsive behavior.
- External CSS.
- JavaScript.
- Forms.
- Animations.
- Flex/Grid layouts.
- Link tracking or URL rewriting.
- Automatic cleanup of real-IMAP smoke test drafts.

## Further Notes

This PRD follows the accepted decision to keep 草稿回复 content separate from 草稿回复样式. The feature should favor predictable business-email presentation over marketing-email flexibility. The implementation should keep the rollback path simple: disabling styled drafts must preserve the current plain-text behavior.
