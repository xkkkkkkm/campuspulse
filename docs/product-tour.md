# Try CampusPulse locally

[简体中文](product-tour.zh-CN.md) · [Back to README](../README.md)

This tour walks through the student, organizer and administrator views of the course project. It uses a demo running on your computer; this repository does not provide a hosted public demo.

Demo records are seeded once. Their dates can become past dates, and restarting does not refresh them. Some activities may be ended, full or awaiting review; follow the status shown on the page.

1. **Start your local demo.** Follow [Download and run](../README.md#download-and-run) for Docker, Python and platform-specific prerequisites. From the repository root, run:

   ```bash
   python3 tools/dev.py init
   python3 tools/dev.py up
   ```

   Open [http://127.0.0.1:8125](http://127.0.0.1:8125) after startup finishes. You should see the CampusPulse welcome page with a **Sign in** button; the first build may take several minutes. If it fails, use the README's [setup troubleshooting](../README.md#common-setup-problems).

2. **Choose a language and sign in as a student.** Use the page's **中文 / EN** selector, then select **Sign in** and use the student demo account in the [README account table](../README.md#download-and-run).

   The interface and translated demo content follow your selection, which is saved in this browser. Original names, messages and user-authored content can remain in their original language. The bottom navigation reads **Discover**, **Activity**, **Teams**, **Messages** and **Profile** in English.

3. **Find an activity and save it.** Open **Activity**, select a category or search a title or tag, then open a result. Check its time, location, description and available places. Click the bookmark icon at the bottom of the detail page.

   The bookmark becomes filled. Open **Profile → Saved activities** to find the activity again. **Profile → Interest tags** also lets you set interests used by the home recommendations.

4. **Try activity registration when available.** On an eligible activity, select **Register now**, fill in the requested registration information and submit. Use fictional contact details for this local exercise.

   The page shows **Registration pending** until the organizer reviews it; **Profile → My activities** keeps the record. Submission does not mean acceptance. If the demo dates have expired, step 8 explains how to publish a future activity for the exercise.

5. **Explore teams and their separate approval flow.** Open **Teams**, filter or search, and inspect a team's description and membership. For an open team with space that you have not joined, select **Apply to join** and submit an introduction.

   Your application becomes pending and appears under **Profile → My teams** while the captain reviews it. Joining a team does not register you for its linked activity, and activity approval does not approve a team application. **Start a team** lets you create your own; use **Profile → Teams I created** to inspect its captain tools.

6. **Look at conversations and notifications.** Use **Contact organizer** on an activity or **Contact creator** on a team card. In a new direct conversation, send a fictional test message before returning to **Messages**; opening the chat alone does not create message history. Open an existing conversation to inspect its history, and use **More notifications** to view notices.

   Team chat requires active membership. Activity chat appears only when the organizer enables it and you have the required access, such as an approved registration. Application notices help track progress; the activity or team page shows the current status.

7. **Ask the assistant without configuring a model.** Use the floating headset button to open **Assistant** and ask “How can I join a team?”.

   The default setup returns a local guide labeled **Help documentation**, with reference links; no API key is needed. Optional model generation is configured by the person running the installation, as explained in the README. To try a ticket, describe a sample problem and explicitly choose **Contact support team**; follow its replies and status under **Support tickets**. Asking a question alone does not create a ticket.

8. **Explore organizer and administrator views.** Switch accounts using **Profile → Sign out of all devices**, then use the organizer or administrator credentials from the [README](../README.md#download-and-run).

   As the organizer, open **Profile → Activities I organize**, then an activity's management page. You can inspect pending registrations, approved participants and chat controls. Reviewing a pending application changes its status for the student. A team captain handles team requests separately through **Teams I created**.

   As the administrator, open **Profile → Administration**. Browse **Activity moderation**, **User management**, **Featured activity placements**, **Tag library** and **Support tickets** to see the platform's review and support tools. An administrator reply to your sample ticket becomes visible in the student's ticket history.

   To exercise registration after the sample dates expire, use **Publish activity** as the organizer and choose future dates and available places. The new activity starts pending review; approve it in **Activity moderation** as the administrator, then return as the student to register. Use separate browser profiles if you want to keep different roles open at the same time.

If you find a confusing step or a bug, [open an issue](https://github.com/xkkkkkkm/campuspulse/issues/new/choose) with the page, steps and expected behavior. For documentation, translation or code changes, read [Contributing](../CONTRIBUTING.md). You can also star the repository to save it for another visit.
