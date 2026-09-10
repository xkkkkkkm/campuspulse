# Campus Activity Prototype Axure Rebuild Plan

## Goal

Rebuild the existing `frontend` inside Axure RP 11 with visual output that matches the current HTML prototype as closely as possible.

This is not a low-fidelity translation. The target is pixel-level visual consistency for teacher review.

## Current Source Scope

The current prototype contains 15 HTML pages:

- `index.html`
- `login.html`
- `register.html`
- `home.html`
- `activity-lobby.html`
- `activity-detail.html`
- `team-lobby.html`
- `team-success.html`
- `messages.html`
- `chat.html`
- `profile.html`
- `profile-edit.html`
- `interest-tags.html`
- `publish-activity.html`
- `admin.html`

## Recommended Axure Strategy

To make the Axure version look identical to the current pages, the safest approach is:

1. Run the existing prototype and capture fixed-size page screenshots.
2. Create Axure pages with the same canvas size.
3. Use each screenshot as the page background or a full-page image.
4. Add transparent hotspots, dynamic panels, and modal overlays only where interaction must be demonstrated.
5. Rebuild only the truly interactive areas as editable Axure widgets when necessary.

This hybrid method is more reliable than redrawing every widget by hand and is the closest path to "exactly the same".

## Why This Approach

- The current prototype is a hand-coded HTML/CSS/JS site, not an Axure source project.
- A direct editable HTML-to-Axure conversion path has not been identified.
- Many pages share a polished visual language that would be time-consuming to redraw widget by widget.
- Teacher review typically focuses on whether the deliverable is in Axure RP and whether the prototype visually and behaviorally matches the design.

## Visual Baseline

The existing design system is defined mainly in:

- `assets/css/style.css`
- `assets/js/script.js`

Important visual traits to preserve:

- White card surfaces with soft shadows
- Rounded corners around 18px to 24px
- Apple-like spacing and typography rhythm
- Light gray background `#f5f5f7`
- Accent blue around `#0071e3`
- Sticky top bars and fixed bottom navigation on major pages
- Modal overlays with centered dialog cards

## Axure Page Tree

Suggested Axure page structure:

- `00 Auth`
- `00 Auth / Landing`
- `00 Auth / Login`
- `00 Auth / Register`
- `01 Main`
- `01 Main / Home`
- `01 Main / Activity Lobby`
- `01 Main / Team Lobby`
- `01 Main / Messages`
- `01 Main / Profile`
- `02 Detail`
- `02 Detail / Activity Detail`
- `02 Detail / Chat`
- `02 Detail / Team Success`
- `03 Profile`
- `03 Profile / Edit Profile`
- `03 Profile / Interest Tags`
- `04 Publish`
- `04 Publish / Publish Activity Or Team`
- `05 Admin`
- `05 Admin / Dashboard`

## Reusable Components In Axure

These should be made as Components or Masters in Axure:

- Auth hero layout
- Auth form card
- Top header bar
- Bottom tab bar with 5 tabs
- Standard primary button
- Standard secondary button
- Modal shell
- Activity card
- Team card
- Tag chip
- Empty state card
- Profile menu item row
- Admin filter bar

## Interaction Mapping

The current JS interactions can be reduced into Axure behaviors like this:

- Page-to-page navigation
- Dynamic panel state switching
- Show/hide modal overlays
- Toggle selected states for chips and tabs
- Simple form validation messages
- Simulated success popups
- Scroll-to-section or open-drawer behaviors

Interactions that should be rebuilt in Axure instead of flattened into screenshots:

- Login page tab switch: password login / SMS login
- Login forgot-password modal
- Register avatar selection and password visibility toggle
- Home search suggestion panel
- Activity detail registration modal
- Activity detail registrations modal
- Profile drawer modal
- Publish page type toggle: activity / team
- Interest tag selection
- Admin list item detail modals

Interactions that can be represented as simple page jumps or hotspot clicks:

- Landing to login/register
- Bottom navigation between main pages
- Home to activity lobby / team lobby
- Lobby to detail pages
- Team success to chat or lobby
- Profile to edit / admin / interest pages

## Page-by-Page Rebuild Notes

### Landing

- Use a full-page screenshot as the base.
- Add hotspots on the "登录" and "注册" buttons.

### Login

- Keep the whole page visual as screenshot-backed.
- Rebuild the login tab area and forgot-password modal as dynamic panels.
- Add success navigation to `Home`.

### Register

- Keep the visual shell as screenshot-backed.
- Rebuild avatar selection, password visibility toggle, and submit state.

### Home

- Use screenshot-backed sections.
- Add hotspots for top actions, entry cards, recommendation cards, and bottom tab bar.
- Rebuild search suggestion drop-down only if the teacher will click through it.

### Activity Lobby

- Use a screenshot-backed list page.
- Make category icons and cards clickable.
- Optional: rebuild filter bar as a modal or inline dynamic panel.

### Activity Detail

- Use screenshot as the main base.
- Rebuild registration modal and team-join modal behavior using dynamic panels.
- Add hotspots for collect, contact, manage, chat, and create-team buttons.

### Team Lobby

- Same method as Activity Lobby.
- Rebuild or simulate filter bar if needed.

### Messages

- Screenshot-backed layout is enough in most cases.
- Add hotspots to open `Chat`.

### Chat

- Screenshot-backed layout.
- Rebuild message input area only if the teacher expects visible send interaction.

### Profile

- Rebuild the drawer modal because it is one of the clearer interaction points.
- Keep the rest of the profile page screenshot-backed.

### Edit Profile

- Rebuild education chips and save button state if demonstration is needed.

### Interest Tags

- Rebuild tag chips as selectable widgets.

### Publish

- One of the most important pages to rebuild partially.
- Rebuild:
  - type toggle
  - tag selection
  - image upload placeholder
  - preview card state
  - submit success jump

### Admin

- Keep dashboard visuals screenshot-backed.
- Add hotspots on list items and buttons.
- Rebuild one or two representative modals only.

## Exact-Fidelity Requirements

If the requirement is "looks exactly the same", these rules should be followed:

- Fix the Axure page width to the same desktop viewport used for screenshots.
- Do not let Axure auto-resize image backgrounds.
- Keep screenshot and hotspot coordinates aligned exactly.
- Reuse the same Chinese text, spacing, button labels, and icon positions.
- Preserve the original image assets such as `suda-logo.jpg`.

## Current Technical Blocker

The repository contains a Spring Boot backend that serves the prototype at port `8080`, but it currently depends on MySQL:

- backend port: `8080`
- database: MySQL on `127.0.0.1:3306`
- current local port check indicates MySQL is not running

Without live API data, several pages will render as empty shells unless we do one of these:

1. Start the real MySQL-backed backend.
2. Build a local mock API server for screenshot generation.
3. Manually prepare static screenshots for dynamic pages.

## Best Execution Path

Recommended order:

1. Bring up a renderable version of the current prototype with either real or mock data.
2. Export fixed-size screenshots for all 15 pages and key modal states.
3. Open Axure RP 11 and create the page tree.
4. Import screenshots page by page.
5. Add hotspots and dynamic panels for the required interactive states.
6. Save as a clean `.rp` project for teacher submission.

## What Can Be Automated Next

The following parts are suitable for immediate automation:

- page inventory
- screenshot asset preparation
- modal/state checklist
- Axure page naming and rebuild order

The following parts likely require GUI control in Axure:

- creating the `.rp` project
- importing screenshots onto pages
- placing hotspots precisely
- configuring dynamic panels
- linking interactions across pages

## Immediate Next Step

To continue toward a truly identical Axure deliverable, the next task should be:

Create a renderable dataset path for the current prototype so that all key pages can be captured as screenshots with their visible content populated.
