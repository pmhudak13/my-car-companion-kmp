-- Allow admins to revoke non-admin roles (mechanic, etc.)
-- Without this policy, RLS silently blocked DELETE on user_roles for all users.
CREATE POLICY "Admins can delete non-admin roles"
ON public.user_roles
FOR DELETE
TO authenticated
USING (
  has_role(auth.uid(), 'admin'::app_role)
  AND role <> 'admin'::app_role
);
