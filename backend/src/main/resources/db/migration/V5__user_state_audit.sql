ALTER TABLE public.user_audit_changes
    DROP CONSTRAINT user_audit_changes_field_name_check;
ALTER TABLE public.user_audit_changes
    ADD CONSTRAINT user_audit_changes_field_name_check
    CHECK (field_name IN ('name', 'email', 'role', 'active'));
