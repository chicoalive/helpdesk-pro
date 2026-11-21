ALTER TABLE public.users
    ADD COLUMN active boolean NOT NULL DEFAULT true,
    ADD COLUMN session_version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.users
    ADD CONSTRAINT users_session_version_nonnegative CHECK (session_version >= 0);