-- Chat: only verified mechanics can start a conversation; everyone else can
-- only reply to someone who already messaged them.
--
-- Before: INSERT only checked sender_id = auth.uid(), so any signed-in user
-- could message any user id. The recipient UPDATE policy allowed rewriting
-- every column (incl. sender_id), which could forge a "prior message" and
-- defeat any thread-based rule (and can_send_notification / chat-photo access).

CREATE OR REPLACE FUNCTION public.is_verified_mechanic(p_user_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $$
  SELECT EXISTS (
    SELECT 1
      FROM public.user_roles r
      JOIN public.mechanic_profiles m ON m.user_id = r.user_id
     WHERE r.user_id = p_user_id
       AND r.role = 'mechanic'
       AND m.verification_status = 'verified'
  );
$$;

CREATE OR REPLACE FUNCTION public.has_received_chat_from(p_me uuid, p_other uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.chat_messages
     WHERE sender_id = p_other AND recipient_id = p_me
  );
$$;

REVOKE EXECUTE ON FUNCTION public.is_verified_mechanic(uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.has_received_chat_from(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_verified_mechanic(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.has_received_chat_from(uuid, uuid) TO authenticated;

DROP POLICY IF EXISTS "Users can insert messages" ON public.chat_messages;
CREATE POLICY "Mechanics start chats, others reply"
  ON public.chat_messages FOR INSERT TO authenticated
  WITH CHECK (
    sender_id = (SELECT auth.uid())
    AND recipient_id <> sender_id
    AND (image_path IS NULL OR image_path LIKE (SELECT auth.uid())::text || '/%')
    AND (
      public.is_verified_mechanic((SELECT auth.uid()))
      OR public.has_received_chat_from((SELECT auth.uid()), recipient_id)
    )
  );

-- Recipients may only flip is_read; nothing else on a message is editable.
REVOKE UPDATE ON public.chat_messages FROM anon, authenticated;
GRANT UPDATE (is_read) ON public.chat_messages TO authenticated;
REVOKE INSERT ON public.chat_messages FROM anon;

DROP POLICY IF EXISTS "Users can mark messages as read" ON public.chat_messages;
CREATE POLICY "Users can mark messages as read"
  ON public.chat_messages FOR UPDATE TO authenticated
  USING (recipient_id = (SELECT auth.uid()))
  WITH CHECK (recipient_id = (SELECT auth.uid()));
