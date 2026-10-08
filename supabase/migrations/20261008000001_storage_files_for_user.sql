-- Files the delete-account function removes before deleting the user. Storage policies make <uid>/ the
-- uploader's own folder in every bucket; chat photos others sent this user live in the sender's folder,
-- but their messages are deleted with the account, so those files would be orphaned too.
CREATE OR REPLACE FUNCTION public.storage_files_for_user(p_user_id UUID)
RETURNS TABLE (bucket_id TEXT, name TEXT)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT o.bucket_id, o.name
    FROM storage.objects o
   WHERE (storage.foldername(o.name))[1] = p_user_id::text
      OR o.owner_id = p_user_id::text
  UNION
  SELECT 'chat-photos', m.image_path
    FROM public.chat_messages m
   WHERE m.recipient_id = p_user_id AND m.image_path IS NOT NULL;
$$;

REVOKE ALL ON FUNCTION public.storage_files_for_user(UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.storage_files_for_user(UUID) TO service_role;
