ALTER TABLE public.users ADD COLUMN edit_version bigint NOT NULL DEFAULT 0;

CREATE TABLE public.user_audit_changes (
    audit_id bigint NOT NULL REFERENCES public.user_audit_logs(id),
    field_name varchar(255) NOT NULL CHECK (field_name IN ('name', 'email', 'role')),
    old_value text,
    new_value text,
    PRIMARY KEY (audit_id, field_name)
);

-- A single transactional counter protects even direct SQL and concurrent writes.
CREATE TABLE public.user_admin_guard (
    id integer PRIMARY KEY CHECK (id = 1),
    active_admins bigint NOT NULL CHECK (active_admins >= 0)
);
INSERT INTO public.user_admin_guard(id, active_admins)
SELECT 1, count(*) FROM public.users WHERE role = 'ADMIN' AND active;

CREATE FUNCTION public.protect_last_active_admin() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    was_admin boolean := false;
    becomes_admin boolean := false;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        was_admin := OLD.role = 'ADMIN' AND OLD.active;
    END IF;
    IF TG_OP <> 'DELETE' THEN
        becomes_admin := NEW.role = 'ADMIN' AND NEW.active;
    END IF;
    IF was_admin AND NOT becomes_admin THEN
        UPDATE public.user_admin_guard SET active_admins = active_admins - 1
        WHERE id = 1 AND active_admins > 1;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Não é permitido remover o último administrador ativo'
                USING ERRCODE = '23514', CONSTRAINT = 'last_active_admin';
        END IF;
    ELSIF NOT was_admin AND becomes_admin THEN
        UPDATE public.user_admin_guard SET active_admins = active_admins + 1 WHERE id = 1;
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER users_last_active_admin
AFTER INSERT OR UPDATE OF role, active OR DELETE ON public.users
FOR EACH ROW EXECUTE FUNCTION public.protect_last_active_admin();
