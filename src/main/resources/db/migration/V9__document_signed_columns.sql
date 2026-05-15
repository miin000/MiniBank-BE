-- Add signed tracking columns to documents
alter table documents
    add column signed_status varchar(32),
    add column signed_by_user_id bigint,
    add column signed_at timestamptz;

create index idx_documents_signed_by on documents(signed_by_user_id);
