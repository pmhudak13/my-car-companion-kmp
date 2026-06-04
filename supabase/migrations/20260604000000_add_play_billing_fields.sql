-- Add Google Play Billing columns to profiles
-- play_purchase_token: the latest purchase token returned by Play Billing; used for verification
-- play_product_id: the Play Console product ID ("premium" or "mechanic_pro")

alter table profiles
  add column if not exists play_purchase_token text,
  add column if not exists play_product_id text;
