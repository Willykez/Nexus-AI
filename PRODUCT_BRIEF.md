# Nexus AI — Product Brief

*(Written the way I'd brief a developer if I were commissioning this app — what it should feel
like to use, not how the code works internally.)*

---

## The pitch

"I want a coding assistant that lives on my phone, that I can point at any AI model I already
pay for, and that actually **does** things instead of just talking about them. When I ask it to
build something, I want to watch it happen — files appearing, code typing itself out — not
stare at a spinner and get a wall of text at the end."

---

## Who's using it and when

Someone who already has an OpenAI/Ollama/DeepSeek/Gemini API key, is away from their laptop, and
wants to sketch out a feature, fix a bug, or scaffold a small project from their phone — on the
train, in bed, between meetings. Not a full IDE replacement; a fast, trustworthy way to turn an
idea into working files without waiting to sit down at a desk.

---

## The core experience, step by step

1. **I open the app and it just works.** It remembers which provider I connected last time. If
   I've never set one up, it nudges me to Settings — it doesn't crash or show a blank screen.

2. **I type what I want in plain English.** "Build me a Kotlin data class for a shopping cart
   with add/remove/total" or "read main.py and fix the off-by-one bug." I hit send.

3. **I see it thinking, not just loading.** The response starts appearing word by word, like
   watching someone type in real time — not a frozen spinner followed by a wall of text
   dumped all at once.

4. **When it decides to touch a file, I see that happen too, live.** Not after the fact — while
   it's happening. A little card pops up saying it's writing `CartViewModel.kt`, I can watch the
   code fill in below it, and a checkmark appears the moment it's done. If it's reading a file
   or listing the project, I see that as its own step too, not hidden inside the chat text.

5. **My actual files update as I watch.** There's a second panel — a simple file tree — and the
   file the agent just touched lights up. I don't have to ask "did it actually create that
   file?" I can just look.

6. **It can do more than one thing per request.** "Scaffold three files for a login screen and
   zip it up for me" shouldn't require three separate messages from me. It should write file
   one, write file two, write file three, then zip — all from a single ask, showing me each
   step as it goes, and telling me in plain language when it's done.

7. **If something goes wrong, it tells me — it doesn't fail silently.** Bad API key, dead
   connection, the model tried to write somewhere it shouldn't have — I want a clear message
   in the chat, not a frozen UI or a crash.

8. **I can switch providers without starting over.** If I decide to try my local Ollama model
   instead of OpenAI mid-conversation, that's a settings change, not a reason to lose my chat
   history.

9. **Nothing it does touches anything outside its own sandbox.** I'm trusting an AI model to
   generate file paths. I never want to worry that a weird response could write or read
   something outside the app's own workspace.

10. **On my phone it's one thing at a time; on a tablet, give me both at once.** Phone screen:
    let me flip between "the conversation" and "what it's building" with a tab. Tablet or wide
    screen: just show me both side by side, I don't need to tap anything.

---

## What "done" looks like — the demo that has to work

A single message: **"Create a Kotlin data class called `Task` with `title`, `isDone`, and
`dueDate`, then create a second file with a function that filters a list of tasks to only the
incomplete ones, then zip both files together."**

Watching that play out, I should see, in order and without me sending a second message:

- Text streaming in explaining the plan
- An action card for `write_file` on `Task.kt`, code appearing live underneath it, card turns
  green
- File tree updates — `Task.kt` shows up, briefly highlighted
- An action card for `write_file` on the filter file, same live behavior
- File tree updates again
- An action card for `zip_project`, then a small banner telling me where the archive landed
- A final plain-English message: "Done — created Task.kt, TaskFilters.kt, and zipped both into
  workspace.zip."

If that entire sequence happens from one message, with live visual feedback at every step and
nothing silently skipped, the app is doing its job.

---

## What I explicitly don't want

- I don't want to have to refresh anything manually to see new files.
- I don't want tool activity buried in a giant text response I have to read carefully to
  understand what actually happened.
- I don't want my API key ever leaving my phone except to the provider I configured.
- I don't want the agent to just keep calling tools forever if it gets confused — it should
  eventually stop and tell me it's stuck, not spin indefinitely.
- I don't want a provider switch to wipe my conversation.

---

## Judging whether it's "good," not just "working"

- Does it *feel* live, or does it feel like a chat app with a progress bar bolted on?
- If I glance at my phone mid-request without reading anything, can I tell what it's doing just
  from the visual state (colors, icons, motion) — running, done, or errored?
- If I hand this to someone who's never seen it, do they understand what happened in the last 30
  seconds without me explaining it?

That's the bar. Everything else is implementation detail.
