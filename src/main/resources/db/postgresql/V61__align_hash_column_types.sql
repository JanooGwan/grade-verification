-- JPA maps SHA-256 values as variable-length strings in both supported databases.
ALTER TABLE evaluation_rule_extraction ALTER COLUMN file_sha256 TYPE varchar(64);
ALTER TABLE student_transcript_import ALTER COLUMN file_sha256 TYPE varchar(64);
