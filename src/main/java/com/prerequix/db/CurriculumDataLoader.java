package com.prerequix.db;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds academic curriculum templates into the SQLite database.
 *
 * <p><b>Dynamic JSON Architecture:</b>
 * This loader reads directly from {@code sample_courses.json} (either from local project root
 * or classpath {@code /data/sample_courses.json} / {@code /data/curricula.json}).
 * Adding or editing a curriculum in {@code sample_courses.json} automatically makes it
 * available in the database and UI without requiring code changes or recompilation.
 */
public class CurriculumDataLoader {

    public record CourseRecord(String id, String code, String title, double credits,
                               String department, String description, List<String> prerequisites) {}

    public record CurriculumDefinition(String id, String name, String department,
                                      String degree, String description, List<CourseRecord> courses) {}

    /**
     * Seeds the curricula and courses from sample_courses.json into the SQLite database.
     * Idempotent: verifies if any new curricula in the JSON are not yet in DB and adds them.
     */
    public static void seed(String dbUrl) throws Exception {
        List<CurriculumDefinition> definitions = loadCurriculaFromJson();
        if (definitions.isEmpty()) {
            System.out.println("[DB] No curricula found in JSON to seed.");
            return;
        }

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            conn.setAutoCommit(false);
            try {
                String insCourse = """
                        INSERT OR IGNORE INTO courses
                            (id, code, title, credits, department, description, status)
                        VALUES (?, ?, ?, ?, ?, ?, 'UNCOMPLETED')""";
                String insPrereq = "INSERT OR IGNORE INTO prerequisites (course_id, prerequisite_id) VALUES (?, ?)";
                String insCurr   = """
                        INSERT OR REPLACE INTO curricula
                            (id, name, department, degree, description, total_courses)
                        VALUES (?, ?, ?, ?, ?, ?)""";
                String insCC     = "INSERT OR IGNORE INTO curriculum_courses (curriculum_id, course_id) VALUES (?, ?)";

                try (PreparedStatement psCourse = conn.prepareStatement(insCourse);
                     PreparedStatement psPrereq = conn.prepareStatement(insPrereq);
                     PreparedStatement psCurr   = conn.prepareStatement(insCurr);
                     PreparedStatement psCC     = conn.prepareStatement(insCC)) {

                    for (CurriculumDefinition def : definitions) {
                        // 1. Insert courses
                        for (CourseRecord c : def.courses()) {
                            psCourse.setString(1, c.id());
                            psCourse.setString(2, c.code());
                            psCourse.setString(3, c.title());
                            psCourse.setDouble(4, c.credits());
                            psCourse.setString(5, c.department());
                            psCourse.setString(6, c.description());
                            psCourse.addBatch();
                        }
                        psCourse.executeBatch();

                        // 2. Insert prerequisites
                        for (CourseRecord c : def.courses()) {
                            for (String pre : c.prerequisites()) {
                                psPrereq.setString(1, c.id());
                                psPrereq.setString(2, pre);
                                psPrereq.addBatch();
                            }
                        }
                        psPrereq.executeBatch();

                        // 3. Insert or update curriculum header
                        psCurr.setString(1, def.id());
                        psCurr.setString(2, def.name());
                        psCurr.setString(3, def.department());
                        psCurr.setString(4, def.degree());
                        psCurr.setString(5, def.description());
                        psCurr.setInt(6, def.courses().size());
                        psCurr.executeUpdate();

                        // 4. Link courses to curriculum
                        for (CourseRecord c : def.courses()) {
                            psCC.setString(1, def.id());
                            psCC.setString(2, c.id());
                            psCC.addBatch();
                        }
                        psCC.executeBatch();
                    }
                }
                conn.commit();
                System.out.println("[DB] Seeded " + definitions.size() + " curricula from JSON data source.");
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Reads JSON data from disk or classpath and parses curriculum definitions.
     */
    public static List<CurriculumDefinition> loadCurriculaFromJson() {
        String jsonContent = readJsonSource();
        if (jsonContent == null || jsonContent.isBlank()) {
            return List.of();
        }

        List<CurriculumDefinition> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonContent);
            if (!root.has("curricula")) {
                return list;
            }

            JSONArray currArr = root.getJSONArray("curricula");
            for (int i = 0; i < currArr.length(); i++) {
                JSONObject cObj = currArr.getJSONObject(i);
                String id     = cObj.optString("id", "CURR_" + (i + 1));
                String name   = cObj.optString("name", "Curriculum " + id);
                String dept   = cObj.optString("department", "General");
                String degree = cObj.optString("degree", "Bachelor of Science");
                String desc   = cObj.optString("description", "");

                List<CourseRecord> courses = new ArrayList<>();
                if (cObj.has("courses")) {
                    JSONArray coursesArr = cObj.getJSONArray("courses");
                    for (int j = 0; j < coursesArr.length(); j++) {
                        JSONObject crs = coursesArr.getJSONObject(j);
                        String cCode  = crs.optString("code", "CRS" + (j + 1));
                        String cId    = crs.optString("id", cCode.replaceAll("\\s+", ""));
                        String cTitle = crs.optString("title", cCode);
                        double cCreds = crs.optDouble("credits", 3.0);
                        String cDept  = crs.optString("department", dept);
                        String cDesc  = crs.optString("description", "");

                        List<String> prereqs = new ArrayList<>();
                        if (crs.has("prerequisites")) {
                            JSONArray prArr = crs.getJSONArray("prerequisites");
                            for (int k = 0; k < prArr.length(); k++) {
                                prereqs.add(prArr.getString(k));
                            }
                        }
                        courses.add(new CourseRecord(cId, cCode, cTitle, cCreds, cDept, cDesc, prereqs));
                    }
                }

                list.add(new CurriculumDefinition(id, name, dept, degree, desc, courses));
            }
        } catch (Exception e) {
            System.err.println("[CurriculumDataLoader] Warning: Failed to parse curricula from JSON: " + e.getMessage());
        }
        return list;
    }

    private static String readJsonSource() {
        // 1. Try local sample_courses.json file on disk
        File localFile = new File("sample_courses.json");
        if (localFile.exists()) {
            try {
                return Files.readString(localFile.toPath(), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }

        // 2. Try classpath /data/sample_courses.json
        try (InputStream in = CurriculumDataLoader.class.getResourceAsStream("/data/sample_courses.json")) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}

        // 3. Try classpath /data/curricula.json
        try (InputStream in = CurriculumDataLoader.class.getResourceAsStream("/data/curricula.json")) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}

        return null;
    }
}