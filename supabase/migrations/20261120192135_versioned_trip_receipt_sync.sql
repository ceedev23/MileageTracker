-- Keep the original client_id for legacy adoption. New records use an account-scoped UUID.
alter table public.trips
    add column sync_id uuid not null default gen_random_uuid(),
    add column sync_updated_at bigint not null default 0 check (sync_updated_at >= 0),
    add column sync_change_id text collate "C" not null default '',
    add column sync_deleted boolean not null default false,
    add constraint trips_user_sync_key unique (user_id, sync_id);

alter table public.gas_receipts
    add column sync_id uuid not null default gen_random_uuid(),
    add column sync_updated_at bigint not null default 0 check (sync_updated_at >= 0),
    add column sync_change_id text collate "C" not null default '',
    add column sync_deleted boolean not null default false,
    add column trip_sync_id uuid,
    add constraint gas_receipts_user_sync_key unique (user_id, sync_id);

-- The conditional update runs while PostgreSQL holds the conflicting row lock.
-- A deletion wins permanently. Timestamps and then a unique change ID order edits.
create function public.keep_latest_mileage_record() returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
declare
    original_headers text := current_setting('request.headers', true);
begin
    if tg_op = 'DELETE' then
        -- Old Android builds request DELETE. Leave the marker even for those builds.
        if current_user = 'authenticated' then
            -- Retain the owner's RLS check while allowing this update to write a hidden row.
            perform set_config('request.headers', (coalesce(nullif(original_headers, '')::jsonb, '{}'::jsonb) ||
                '{"x-voomlogs-sync":"2"}'::jsonb)::text, true);
            execute format('update public.%I set sync_deleted = true, sync_updated_at = $1, sync_change_id = $2 where id = $3', tg_table_name)
                using (extract(epoch from clock_timestamp()) * 1000)::bigint, gen_random_uuid()::text, old.id;
            perform set_config('request.headers', coalesce(original_headers, ''), true);
            return null;
        end if;
        return old;
    end if;
    if tg_op = 'UPDATE' then
        if new.sync_id is distinct from old.sync_id or new.user_id is distinct from old.user_id then
            raise exception 'Record identity is immutable';
        end if;
        if old.sync_deleted then
            return null;
        end if;
        -- Legacy records can still be edited by old builds until a versioned edit is saved.
        if not new.sync_deleted and old.sync_updated_at = 0 and new.sync_updated_at = 0 and
            (nullif(original_headers, '')::jsonb ->> 'x-voomlogs-sync') is distinct from '2' then
            return new;
        end if;
        if not new.sync_deleted and
            (new.sync_updated_at, new.sync_change_id collate "C") <=
            (old.sync_updated_at, old.sync_change_id collate "C") then
            -- Photo opt-in can add the photo to an otherwise identical receipt revision.
            if tg_table_name = 'gas_receipts' and new.sync_change_id <> '' and
                new.sync_updated_at = old.sync_updated_at and new.sync_change_id = old.sync_change_id then
                if old.image_path is null or old.image_path = '' then
                    old.image_path := new.image_path;
                    return old;
                end if;
            end if;
            return null;
        end if;
        if tg_table_name = 'gas_receipts' then
            if new.image_path is null or new.image_path = '' then
                new.image_path := old.image_path;
            end if;
        end if;
        new.updated_at := now();
    end if;
    if new.sync_deleted then
        -- Preserve identity and the legacy matching dates for clients upgrading later.
        if tg_table_name = 'trips' then
            new.type := null;
            new.start_address := null;
            new.end_address := null;
            new.purpose := null;
            new.vehicle := null;
            new.path_json := '[]';
        else
            new.merchant := null;
            new.total_amount := 0;
            new.fuel_type := null;
            new.gallons_or_liters := null;
            new.price_per_unit := null;
            new.receipt_date := null;
            new.payment_method := null;
            new.raw_extraction := null;
            new.trip_id := null;
            new.trip_sync_id := null;
        end if;
    end if;
    return new;
end;
$$;
revoke execute on function public.keep_latest_mileage_record() from public, anon, authenticated;

create trigger trips_latest_sync before insert or update or delete on public.trips
    for each row execute function public.keep_latest_mileage_record();
create trigger gas_receipts_latest_sync before insert or update or delete on public.gas_receipts
    for each row execute function public.keep_latest_mileage_record();

-- Older builds interpret cloud identity as a device-local number.
-- Restrict their reads to live legacy rows so UUID records cannot collide in their local DB.
create policy "Trips use the appropriate sync protocol"
on public.trips as restrictive for select to authenticated
using (
    nullif(current_setting('request.headers', true), '')::jsonb ->> 'x-voomlogs-sync' = '2'
    or (client_id is not null and not sync_deleted)
);
create policy "Receipts use the appropriate sync protocol"
on public.gas_receipts as restrictive for select to authenticated
using (
    nullif(current_setting('request.headers', true), '')::jsonb ->> 'x-voomlogs-sync' = '2'
    or (client_id is not null and not sync_deleted)
);
notify pgrst, 'reload schema';
