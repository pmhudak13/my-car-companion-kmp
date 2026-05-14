-- Add per-type email notification preference columns to notification_preferences.
-- All default to true so existing users get email notifications unless they opt out.
ALTER TABLE public.notification_preferences
  ADD COLUMN IF NOT EXISTS email_new_messages      boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS email_mechanic_updates  boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS email_oil_change        boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS email_tire_rotation     boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS email_registration      boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS email_custom_reminders  boolean NOT NULL DEFAULT true;
