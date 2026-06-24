SET @db = DATABASE();

SET @has_sort_order = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'contract_attachment'
      AND COLUMN_NAME = 'sort_order'
);
SET @stmt = IF(
    @has_sort_order = 0,
    'ALTER TABLE contract_attachment ADD COLUMN sort_order INT NULL DEFAULT 0 AFTER attach_type',
    'SELECT ''contract_attachment.sort_order already exists'''
);
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @has_remark = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'contract_attachment'
      AND COLUMN_NAME = 'remark'
);
SET @stmt = IF(
    @has_remark = 0,
    'ALTER TABLE contract_attachment ADD COLUMN remark VARCHAR(500) NULL AFTER sort_order',
    'SELECT ''contract_attachment.remark already exists'''
);
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;
