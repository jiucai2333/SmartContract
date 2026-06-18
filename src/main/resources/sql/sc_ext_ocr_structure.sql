DELIMITER //

DROP PROCEDURE IF EXISTS add_contract_attachment_column//
CREATE PROCEDURE add_contract_attachment_column(
    IN column_name_value VARCHAR(64),
    IN column_definition_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'contract_attachment'
          AND COLUMN_NAME = column_name_value
    ) THEN
        SET @ddl = CONCAT(
                'ALTER TABLE contract_attachment ADD COLUMN ',
                column_name_value,
                ' ',
                column_definition_value
        );
        PREPARE statement_to_run FROM @ddl;
        EXECUTE statement_to_run;
        DEALLOCATE PREPARE statement_to_run;
    END IF;
END//

CALL add_contract_attachment_column(
        'ocr_raw_json',
        'LONGTEXT NULL COMMENT ''PaddleOCR raw JSON or JSONL response'' AFTER ocr_text')//
CALL add_contract_attachment_column(
        'ocr_parse_json',
        'LONGTEXT NULL COMMENT ''Normalized OCR document JSON'' AFTER ocr_raw_json')//
CALL add_contract_attachment_column(
        'qwen_layout_json',
        'LONGTEXT NULL COMMENT ''Qwen layout analysis JSON'' AFTER ocr_parse_json')//
CALL add_contract_attachment_column(
        'parse_source',
        'VARCHAR(30) NULL COMMENT ''paddleocr, qwen or fallback'' AFTER qwen_layout_json')//
CALL add_contract_attachment_column(
        'approximate',
        'TINYINT(1) NULL COMMENT ''Whether layout restoration is approximate'' AFTER parse_source')//
CALL add_contract_attachment_column(
        'parse_warnings',
        'LONGTEXT NULL COMMENT ''JSON warning list'' AFTER approximate')//
CALL add_contract_attachment_column(
        'ocr_model',
        'VARCHAR(100) NULL COMMENT ''OCR model name'' AFTER parse_warnings')//
CALL add_contract_attachment_column(
        'qwen_model',
        'VARCHAR(100) NULL COMMENT ''Qwen layout model name'' AFTER ocr_model')//
CALL add_contract_attachment_column(
        'ocr_duration_ms',
        'BIGINT NULL COMMENT ''OCR duration in milliseconds'' AFTER qwen_model')//
CALL add_contract_attachment_column(
        'qwen_duration_ms',
        'BIGINT NULL COMMENT ''Qwen layout duration in milliseconds'' AFTER ocr_duration_ms')//

DROP PROCEDURE add_contract_attachment_column//

DELIMITER ;
