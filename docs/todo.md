## To do

- Check-in lists
  - Search - phone number
  - Search - first and last name
- Misc
  - SGC logo on labels
  - Draw, refactor, decouple (ex, move logic from Fragments to Services, apiService and repository holders)
    - Also resolve Person vs Guest vs GuestChild vs FamilyMember
    - Better align these with Breeze's strange models (asPerson, etc)
    - Align and/or combine addGuestToBreeze with addGuestToRepository
      - If Breeze is online, can just fetch the new person from Breeze and add it to the repository
  - Remote logs/monitoring. Crashlytics? PostHog? (https://posthog.com/docs/error-tracking)
  - Switch to Material for views, validation, etc?