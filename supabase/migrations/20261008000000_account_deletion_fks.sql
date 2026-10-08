-- Account deletion (delete-account edge function -> auth.admin.deleteUser) failed for 6 of 14 users:
-- references to auth.users with no ON DELETE rule blocked it, and an audit trigger wrote a NULL user.

-- 1. Chat: a deleted user's conversations go with them.
ALTER TABLE public.chat_messages DROP CONSTRAINT IF EXISTS chat_messages_sender_id_fkey,
  ADD CONSTRAINT chat_messages_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE public.chat_messages DROP CONSTRAINT IF EXISTS chat_messages_recipient_id_fkey,
  ADD CONSTRAINT chat_messages_recipient_id_fkey FOREIGN KEY (recipient_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- 2. Approval records are the shop's proof of authorization: keep them, approver id and all, after the
--    customer leaves. No FK, because SET NULL would trip guard_estimate_approval and erase who approved.
ALTER TABLE public.mechanic_jobs DROP CONSTRAINT IF EXISTS mechanic_jobs_estimate_approved_by_fkey;
ALTER TABLE public.mechanic_job_issues DROP CONSTRAINT IF EXISTS mechanic_job_issues_responded_by_fkey;

-- 3. A gift outlives the admin who gave it.
ALTER TABLE public.gifted_subscriptions ALTER COLUMN gifted_by DROP NOT NULL;

-- 4. Invoices: the mechanic's go with them; a customer reference is cleared.
ALTER TABLE public.mechanic_invoices DROP CONSTRAINT IF EXISTS mechanic_invoices_mechanic_user_id_fkey,
  ADD CONSTRAINT mechanic_invoices_mechanic_user_id_fkey FOREIGN KEY (mechanic_user_id) REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE public.mechanic_invoices DROP CONSTRAINT IF EXISTS mechanic_invoices_client_user_id_fkey,
  ADD CONSTRAINT mechanic_invoices_client_user_id_fkey FOREIGN KEY (client_user_id) REFERENCES auth.users(id) ON DELETE SET NULL;

-- 5. A mechanic's own job rows (normally removed with the job; explicit so nothing can block deletion).
ALTER TABLE public.mechanic_job_line_items DROP CONSTRAINT IF EXISTS mechanic_job_line_items_mechanic_user_id_fkey,
  ADD CONSTRAINT mechanic_job_line_items_mechanic_user_id_fkey FOREIGN KEY (mechanic_user_id) REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE public.mechanic_job_images DROP CONSTRAINT IF EXISTS mechanic_job_images_mechanic_user_id_fkey,
  ADD CONSTRAINT mechanic_job_images_mechanic_user_id_fkey FOREIGN KEY (mechanic_user_id) REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE public.mechanic_job_media DROP CONSTRAINT IF EXISTS mechanic_job_media_mechanic_user_id_fkey,
  ADD CONSTRAINT mechanic_job_media_mechanic_user_id_fkey FOREIGN KEY (mechanic_user_id) REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE public.mechanic_job_issues DROP CONSTRAINT IF EXISTS mechanic_job_issues_mechanic_user_id_fkey,
  ADD CONSTRAINT mechanic_job_issues_mechanic_user_id_fkey FOREIGN KEY (mechanic_user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- 6. Deletion runs as the service role (auth.uid() is NULL); log the affected user instead,
--    like audit_role_changes already does.
CREATE OR REPLACE FUNCTION public.audit_gifted_subscriptions()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    INSERT INTO public.audit_logs (user_id, action, table_name, record_id, new_data)
    VALUES (COALESCE(auth.uid(), NEW.gifted_by, NEW.user_id), 'SUBSCRIPTION_GIFTED', 'gifted_subscriptions', NEW.id,
            jsonb_build_object('user_id', NEW.user_id, 'gifted_by', NEW.gifted_by, 'reason', NEW.reason));
    RETURN NEW;
  ELSIF TG_OP = 'DELETE' THEN
    INSERT INTO public.audit_logs (user_id, action, table_name, record_id, old_data)
    VALUES (COALESCE(auth.uid(), OLD.user_id), 'SUBSCRIPTION_REVOKED', 'gifted_subscriptions', OLD.id,
            jsonb_build_object('user_id', OLD.user_id, 'gifted_by', OLD.gifted_by));
    RETURN OLD;
  END IF;
  RETURN NULL;
END; $$;
