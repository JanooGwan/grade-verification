INSERT INTO university_import_profile (
    university_id, source_format, name, schema_version, replaces_previous_data,
    column_mapping, active, created_at, updated_at
)
SELECT id, 'MJC_SOURCE_WORKBOOK_V1', '명지전문대 통합 Excel', 'v1', TRUE,
       '{"sheets":["01_지원자정보","04_학생부기본정보","05_교과학습발달상황_SubjectScore"]}',
       active, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM university
WHERE code = 'MJC'
  AND NOT EXISTS (
      SELECT 1
      FROM university_import_profile profile
      WHERE profile.university_id = university.id
        AND profile.source_format = 'MJC_SOURCE_WORKBOOK_V1'
        AND profile.schema_version = 'v1'
  );
