-- Users could update their own profiles/mechanic_profiles rows without column limits, so anyone
-- could grant themselves Premium/Mechanic Pro or mark their shop verified. Requests from the
-- client (anon/authenticated) now keep the protected columns unchanged unless the caller is an
-- admin. Edge functions (service_role) and SECURITY DEFINER functions (run as owner) are unaffected.

CREATE OR REPLACE FUNCTION public.protect_profile_billing_columns()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
  IF current_user NOT IN ('anon', 'authenticated') OR public.has_role(auth.uid(), 'admin') THEN
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    NEW.is_premium          := false;
    NEW.is_mechanic_pro     := false;
    NEW.subscription_tier   := 'free';
    NEW.stripe_customer_id  := NULL;
    NEW.play_product_id     := NULL;
    NEW.play_purchase_token := NULL;
  ELSE
    NEW.is_premium          := OLD.is_premium;
    NEW.is_mechanic_pro     := OLD.is_mechanic_pro;
    NEW.subscription_tier   := OLD.subscription_tier;
    NEW.stripe_customer_id  := OLD.stripe_customer_id;
    NEW.play_product_id     := OLD.play_product_id;
    NEW.play_purchase_token := OLD.play_purchase_token;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_protect_profile_billing_columns ON public.profiles;
CREATE TRIGGER trg_protect_profile_billing_columns
  BEFORE INSERT OR UPDATE ON public.profiles
  FOR EACH ROW EXECUTE FUNCTION public.protect_profile_billing_columns();

CREATE OR REPLACE FUNCTION public.protect_mechanic_verification_columns()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
  IF current_user NOT IN ('anon', 'authenticated') OR public.has_role(auth.uid(), 'admin') THEN
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    NEW.verification_status := 'pending';
    NEW.verified_at         := NULL;
    NEW.verification_notes  := NULL;
    NEW.rating              := 0;
    NEW.total_jobs          := 0;
  ELSE
    NEW.verification_status := OLD.verification_status;
    NEW.verified_at         := OLD.verified_at;
    NEW.verification_notes  := OLD.verification_notes;
    NEW.rating              := OLD.rating;
    NEW.total_jobs          := OLD.total_jobs;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_protect_mechanic_verification_columns ON public.mechanic_profiles;
CREATE TRIGGER trg_protect_mechanic_verification_columns
  BEFORE INSERT OR UPDATE ON public.mechanic_profiles
  FOR EACH ROW EXECUTE FUNCTION public.protect_mechanic_verification_columns();
