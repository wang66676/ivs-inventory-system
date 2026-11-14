ALTER TABLE ivs.purchase_invoice ADD COLUMN pdf_file_name varchar(255);
ALTER TABLE ivs.purchase_invoice ADD COLUMN pdf_storage_key varchar(500);
ALTER TABLE ivs.purchase_invoice ADD COLUMN extracted_text text;
