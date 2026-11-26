ALTER TABLE public.tickets ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE public.tickets ADD CONSTRAINT tickets_version_nonnegative CHECK (version >= 0);
