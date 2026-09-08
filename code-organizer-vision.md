# Code Organizer — How This App Should Feel

*A brief from the person who's going to use it every day, not a spec sheet.*

## The one-sentence pitch

I want to open my phone, point it at an Android project folder, and talk to it like I'd talk to a teammate who already read the whole codebase — "what's messy in here," "fix this," "rename that," "show me the structure" — and have it actually touch the files, not just talk about them.

## Who's using this

Me, standing at a bus stop or lying in bed, with one thumb and patchy LTE. Not me at a desk with a keyboard. Every decision about how this app behaves should be checked against that person first.

## The first 30 seconds

I open the app. It should immediately be obvious what to do: pick a project folder, pick an AI provider, paste in a key if I need one. No walls of text, no forced tour. If something's missing — no folder attached, no API key — say so plainly in one place, not as a mystery error three screens later.

I should never wonder "did that button do anything." Every tap gets an immediate, visible reaction.

## The core moment: talking to my project

This is the whole app. Everything else is scaffolding around this one screen.

- I type what I want in plain language. I don't format it, I don't think about tool names, I don't explain file paths unless I want to.
- The reply starts appearing almost immediately — even if it's just "thinking" for a second — because a blank screen makes me assume it's broken.
- While it's working, I want to *see* it working: which file it's reading, which one it just changed, in a compact trail I can glance at or expand, not a wall of raw JSON or "Unknown tool: ...". If something in that trail doesn't make sense, that's a bug, not a detail I should have to interpret.
- The answer itself should read like a person wrote it — headings, bullet points, bold for the thing that matters, and real code blocks for real code, not asterisks and backticks sitting there as literal punctuation. If it's showing me code, I want a one-tap way to grab just that snippet, and a one-tap way to grab the whole reply, without hunting for a menu.
- Anything I typed, I should be able to select and copy too. It's my conversation as much as the assistant's.
- The input box is mine to fill however I want, including pasting something huge. It should never do something weird like swallowing the send button off the bottom of the screen because I pasted three paragraphs. It should just... handle it. Scroll internally, stay usable, keep the button where I can reach it.

## Picking up where I left off

I will close this app mid-task constantly — a call comes in, I lock the phone, whatever. When I come back, my conversation should still be there. And I want a history I can actually browse: what did I ask this app last Tuesday about the login screen? Let me find it, tap it, and be right back in that context — same provider, same model, same file state of mind — not staring at a blank new chat wondering what I was doing.

Deleting an old conversation should feel safe and final — I ask, it's gone, no ambiguity about whether it's "really" gone.

## The other way I work: dumping a whole project in

Sometimes I don't want a conversation. I want to paste an entire project's source — everything, all the files, one giant blob — and say "organize this into a real folder structure." That's a different mode from chatting, and it's allowed to feel different, but it shouldn't feel like a lesser citizen of the app. If I paste something enormous there too, same rule applies: the app adapts to my input, not the other way around.

And when it's working on that — building out folders, writing files — I want the same feeling of "I can see it thinking" that the chat gives me. Not a spinner and silence. If the chat screen shows me its work happening live, this screen should eventually feel like a sibling to it, not a step behind it.

## Providers and settings

I might use whatever AI account I already have — I don't want to be locked to one company. Switching providers should be quick and obvious, and it should be clear which one I'm currently talking to at all times, right there in the input area, not buried in settings. Settings itself should be the boring, predictable kind of screen: my keys, my models, my folder, nothing surprising.

## What "broken" looks like to me (please avoid these)

- A response that never starts, with no sign anything is happening.
- Raw technical junk leaking into a reply — tool names, JSON, stray formatting characters — instead of a clean human answer.
- Losing my conversation because I switched apps for ten seconds.
- A tap that does nothing, or does something different than what it looked like it would do.
- Being told something failed with no plain-English reason and no next step.

## What "delightful" looks like to me

- It feels fast even when it isn't — because it's always showing me *something* is happening.
- It never makes me retype something I already typed.
- Copying anything — a whole reply, one code block, one message — is always exactly one tap away.
- I can trust it enough to let it actually touch my files, because it always shows its work before and while it does.
- It feels like one coherent app, not a chat screen bolted onto a file tool bolted onto a settings page. Same visual language, same "it's thinking" feeling, everywhere.

## The test I'll judge it by

Hand me the phone, don't explain anything, and watch me try to fix a bug in a real project using only this app. If I never have to ask "wait, what just happened," you've built the right thing.
