-- Preserve legacy rows without inventing a historical deadline or resolution time.
ALTER TABLE public.tickets ADD COLUMN resolved_at timestamp with time zone;
ALTER TABLE public.tickets ADD COLUMN sla_resolution_deadline timestamp with time zone;
ALTER TABLE public.tickets ADD CONSTRAINT ticket_sla_resolution_pair CHECK (
    (resolved_at IS NULL AND sla_resolution_deadline IS NULL)
    OR (resolved_at IS NOT NULL AND sla_resolution_deadline IS NOT NULL AND resolved_at >= created_at AND sla_resolution_deadline >= created_at)
);
