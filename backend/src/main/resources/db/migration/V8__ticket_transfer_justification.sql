ALTER TABLE public.ticket_audit_logs ADD COLUMN justification text;
ALTER TABLE public.ticket_audit_logs ADD CONSTRAINT ticket_audit_justification_valid CHECK (justification IS NULL OR (length(btrim(justification)) > 0 AND length(justification) <= 2000));
