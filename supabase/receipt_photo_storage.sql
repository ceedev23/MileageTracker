-- Development receipt storage. Photos are private; no signed/public links are needed.
-- Run only on the confirmed development project. No existing receipts are deleted.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('receipt-photos', 'receipt-photos', false, 10485760, array['image/jpeg'])
on conflict (id) do update set public = false,
  file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Receipt photos belong to their user" on storage.objects;
create policy "Receipt photos belong to their user"
on storage.objects for all to authenticated
using (bucket_id = 'receipt-photos' and (storage.foldername(name))[1] = (select auth.uid())::text)
with check (bucket_id = 'receipt-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- Receipt details are personal information too. Guests keep receipts locally.
drop policy if exists "Anon users can access unassigned gas receipts" on public.gas_receipts;
