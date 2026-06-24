# Controlled HTML template for draft reply styling

Status: accepted

Automatic 草稿回复 will continue to treat the AI response as plain text, and the backend will render any styled HTML through a controlled single-column email template with inline CSS and a plain-text fallback. We chose this over AI-generated HTML, arbitrary CSS, or raw HTML editing because email client CSS support is inconsistent, styled replies need a fast rollback path, and the system must keep 草稿回复 content separate from 草稿回复样式.

## Consequences

- 草稿回复样式 is configured through a limited backend configuration surface, not arbitrary CSS.
- Styled drafts are saved as `multipart/alternative` with both `text/plain` and `text/html` parts.
- Template components are limited to page header, body, page footer, text signature, safe links, one configured CTA link button, and an HTTPS logo image with alt text.
- External CSS, JavaScript, forms, complex animation, Flex/Grid layout, raw webpage HTML, CID images, image signatures, and arbitrary HTML/CSS editors are outside the first version.
