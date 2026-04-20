alter table mechanic_profiles
  add column if not exists google_place_url text,
  add column if not exists yelp_url text;
