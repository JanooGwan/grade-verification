-- The MySQL import stores tinyint(1) flags as smallint. Reject unexpected values
-- rather than silently changing their meaning during conversion.
DO $$
DECLARE col record; bad boolean;
BEGIN
  FOR col IN SELECT * FROM (VALUES
    ('admission_track','active'), ('evaluation_rule','active'),
    ('evaluation_rule','include_third_year_second_semester'),
    ('evaluation_rule','include_third_year_second_semester_for_graduates'),
    ('evaluation_rule','include_professional_courses'), ('evaluation_rule','apply_grade_weights'),
    ('evaluation_rule','normalize_grade_weights'), ('evaluation_rule_extraction','apply_grade_weights'),
    ('evaluation_rule_extraction','include_third_year_second_semester'), ('recruitment_unit','active'),
    ('student_school_violence_action','active'), ('student_transcript_course','career_subject'),
    ('student_transcript_course','professional_course'), ('student_transcript_course','vocational_training_semester'),
    ('student_transcript_import_course','career_subject'), ('student_transcript_import_course','professional_course'),
    ('university','active'), ('university_export_profile','active'),
    ('university_import_profile','replaces_previous_data'), ('university_import_profile','active')
  ) AS flags(table_name, column_name)
  LOOP
    EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I WHERE %I NOT IN (0, 1))', col.table_name, col.column_name) INTO bad;
    IF bad THEN RAISE EXCEPTION 'Unexpected boolean value in %.%', col.table_name, col.column_name; END IF;
  END LOOP;
END $$;

ALTER TABLE admission_track ALTER COLUMN active DROP DEFAULT, ALTER COLUMN active TYPE boolean USING active = 1, ALTER COLUMN active SET DEFAULT true;
ALTER TABLE evaluation_rule
 ALTER COLUMN active DROP DEFAULT, ALTER COLUMN active TYPE boolean USING active = 1, ALTER COLUMN active SET DEFAULT true,
 ALTER COLUMN include_third_year_second_semester DROP DEFAULT, ALTER COLUMN include_third_year_second_semester TYPE boolean USING include_third_year_second_semester = 1, ALTER COLUMN include_third_year_second_semester SET DEFAULT false,
 ALTER COLUMN include_third_year_second_semester_for_graduates DROP DEFAULT, ALTER COLUMN include_third_year_second_semester_for_graduates TYPE boolean USING include_third_year_second_semester_for_graduates = 1, ALTER COLUMN include_third_year_second_semester_for_graduates SET DEFAULT false,
 ALTER COLUMN include_professional_courses DROP DEFAULT, ALTER COLUMN include_professional_courses TYPE boolean USING include_professional_courses = 1, ALTER COLUMN include_professional_courses SET DEFAULT false,
 ALTER COLUMN apply_grade_weights DROP DEFAULT, ALTER COLUMN apply_grade_weights TYPE boolean USING apply_grade_weights = 1, ALTER COLUMN apply_grade_weights SET DEFAULT true,
 ALTER COLUMN normalize_grade_weights DROP DEFAULT, ALTER COLUMN normalize_grade_weights TYPE boolean USING normalize_grade_weights = 1, ALTER COLUMN normalize_grade_weights SET DEFAULT false;
ALTER TABLE evaluation_rule_extraction
 ALTER COLUMN apply_grade_weights TYPE boolean USING apply_grade_weights = 1,
 ALTER COLUMN include_third_year_second_semester TYPE boolean USING include_third_year_second_semester = 1;
ALTER TABLE recruitment_unit ALTER COLUMN active DROP DEFAULT, ALTER COLUMN active TYPE boolean USING active = 1, ALTER COLUMN active SET DEFAULT true;
ALTER TABLE student_school_violence_action ALTER COLUMN active DROP DEFAULT, ALTER COLUMN active TYPE boolean USING active = 1, ALTER COLUMN active SET DEFAULT true;
ALTER TABLE student_transcript_course
 ALTER COLUMN career_subject DROP DEFAULT, ALTER COLUMN career_subject TYPE boolean USING career_subject = 1, ALTER COLUMN career_subject SET DEFAULT false,
 ALTER COLUMN professional_course DROP DEFAULT, ALTER COLUMN professional_course TYPE boolean USING professional_course = 1, ALTER COLUMN professional_course SET DEFAULT false,
 ALTER COLUMN vocational_training_semester DROP DEFAULT, ALTER COLUMN vocational_training_semester TYPE boolean USING vocational_training_semester = 1, ALTER COLUMN vocational_training_semester SET DEFAULT false;
ALTER TABLE student_transcript_import_course
 ALTER COLUMN career_subject DROP DEFAULT, ALTER COLUMN career_subject TYPE boolean USING career_subject = 1, ALTER COLUMN career_subject SET DEFAULT false,
 ALTER COLUMN professional_course DROP DEFAULT, ALTER COLUMN professional_course TYPE boolean USING professional_course = 1, ALTER COLUMN professional_course SET DEFAULT false;
ALTER TABLE university ALTER COLUMN active DROP DEFAULT, ALTER COLUMN active TYPE boolean USING active = 1, ALTER COLUMN active SET DEFAULT true;
ALTER TABLE university_export_profile ALTER COLUMN active DROP DEFAULT, ALTER COLUMN active TYPE boolean USING active = 1, ALTER COLUMN active SET DEFAULT true;
ALTER TABLE university_import_profile
 ALTER COLUMN replaces_previous_data DROP DEFAULT, ALTER COLUMN replaces_previous_data TYPE boolean USING replaces_previous_data = 1, ALTER COLUMN replaces_previous_data SET DEFAULT true,
 ALTER COLUMN active DROP DEFAULT, ALTER COLUMN active TYPE boolean USING active = 1, ALTER COLUMN active SET DEFAULT true;
