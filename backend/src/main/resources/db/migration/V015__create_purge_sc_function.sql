-- Phase 7.7: Archive summary PURGE_APPROVED status and purge_sc(p_sc_id uuid) function

ALTER TABLE sc_archive_summary
    DROP CONSTRAINT chk_sc_archive_summary_status,
    ADD CONSTRAINT chk_sc_archive_summary_status
        CHECK (status IN ('EXPORTED', 'PURGE_APPROVED', 'PURGED'));

CREATE OR REPLACE FUNCTION purge_sc(p_sc_id UUID)
RETURNS TABLE(table_name TEXT, deleted_count BIGINT) AS $$
DECLARE
    v_sc_status      VARCHAR(16);
    v_summary_id     UUID;
    v_summary_status VARCHAR(16);
    v_approved_by    UUID;
    v_approved_at    TIMESTAMPTZ;
    v_cnt            BIGINT;
    v_counts         JSONB := '{}'::jsonb;
BEGIN
    SELECT status INTO v_sc_status FROM sc_headers WHERE id = p_sc_id;
    IF v_sc_status IS NULL OR v_sc_status <> 'ARCHIVED' THEN
        RAISE EXCEPTION 'Cannot purge SC %: sc_headers.status must be ARCHIVED (actual: %)', p_sc_id, v_sc_status;
    END IF;

    SELECT id, status, approved_by, approved_at
      INTO v_summary_id, v_summary_status, v_approved_by, v_approved_at
      FROM sc_archive_summary
     WHERE sc_id = p_sc_id
     ORDER BY archived_at DESC
     LIMIT 1;

    IF v_summary_id IS NULL OR v_summary_status <> 'PURGE_APPROVED' OR v_approved_by IS NULL OR v_approved_at IS NULL THEN
        RAISE EXCEPTION 'Cannot purge SC %: approved sc_archive_summary with status PURGE_APPROVED required', p_sc_id;
    END IF;

    PERFORM set_config('velan.purge_active', 'true', true);

    -- Emit STORAGE_DELETE outbox events for all SC-owned files before deleting file rows
    INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status, sc_id)
    SELECT
        gen_random_uuid(),
        'FILE',
        f.id::text,
        'STORAGE_DELETE',
        jsonb_build_object('file_id', f.id, 'storage_key', f.storage_key),
        'PENDING',
        p_sc_id
    FROM files f
    WHERE f.sc_id = p_sc_id;

    DELETE FROM measurement_values WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'measurement_values'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM qc_inspections WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'qc_inspections'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM pm_dispositions WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'pm_dispositions'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM piece_replacement_lineage WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'piece_replacement_lineage'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM qc_exceptions WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'qc_exceptions'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM operator_values WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'operator_values'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM piece_events WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'piece_events'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM piece_operations WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'piece_operations'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    -- Break circular FK between sc_product_template_steps.scoped_piece_id and pieces
    UPDATE sc_product_template_steps SET scoped_piece_id = NULL WHERE sc_id = p_sc_id AND scoped_piece_id IS NOT NULL;

    DELETE FROM pieces WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'pieces'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM sc_product_dynamic_values WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'sc_product_dynamic_values'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM sc_product_step_parameters WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'sc_product_step_parameters'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM sc_product_node_statuses WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'sc_product_node_statuses'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM sc_product_template_steps WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'sc_product_template_steps'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM drawing_documents WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'drawing_documents'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM vendor_dc_trackers WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'vendor_dc_trackers'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM sc_revisions WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'sc_revisions'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM import_records WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'import_records'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM sc_products WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'sc_products'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM files WHERE sc_id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'files'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    DELETE FROM sc_headers WHERE id = p_sc_id; GET DIAGNOSTICS v_cnt = ROW_COUNT;
    table_name := 'sc_headers'; deleted_count := v_cnt; v_counts := v_counts || jsonb_build_object(table_name, v_cnt); RETURN NEXT;

    INSERT INTO audit_logs (id, sc_id, actor_id, action, entity_type, entity_id, details)
    VALUES (gen_random_uuid(), p_sc_id, v_approved_by, 'SC_PURGED', 'SC_HEADER', p_sc_id::text, v_counts);

    UPDATE sc_archive_summary
       SET status = 'PURGED',
           purged_at = NOW()
     WHERE id = v_summary_id;
END;
$$ LANGUAGE plpgsql;
