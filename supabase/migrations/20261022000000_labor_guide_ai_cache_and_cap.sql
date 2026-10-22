-- Labor guide, part 2: AI estimates are a Mechanic Pro feature. Every Claude answer is cached so the
-- same car + repair is free the second time (for every mechanic), and fresh Claude calls are logged per
-- user so the labor-guide edge function can cap each mechanic's monthly spend.
-- Only that function touches these tables (service role); RLS on with no policies blocks everyone else.

CREATE TABLE public.labor_guide_ai_cache (
  cache_key  TEXT PRIMARY KEY,          -- "year|make|model|repair", lowercased and trimmed
  hours      NUMERIC NOT NULL,
  note       TEXT,
  parts      JSONB NOT NULL DEFAULT '[]', -- [{ "name": text, "low": number, "high": number }]
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.labor_guide_ai_lookups (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id    UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX labor_guide_ai_lookups_user_month ON public.labor_guide_ai_lookups (user_id, created_at);

ALTER TABLE public.labor_guide_ai_cache   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.labor_guide_ai_lookups ENABLE ROW LEVEL SECURITY;
