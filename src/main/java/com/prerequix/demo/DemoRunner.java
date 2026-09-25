package com.prerequix.demo;

import com.prerequix.db.DatabaseManager;
import com.prerequix.model.Course;
import com.prerequix.model.CourseStatus;
import com.prerequix.net.CourseApiClient;
import com.prerequix.net.RemoteCourseParser;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * Standalone demonstration runner (no JavaFX required).
 *
 * <p>Shows two things:
 * <ol>
 *   <li><b>SQLite Database</b> – seeds sample data, then prints both
 *       the {@code courses} and {@code prerequisites} tables via direct JDBC.</li>
 *   <li><b>HTTP + JSON Parsing</b> – fetches the remote
 *       {@code sample_courses.json} from GitHub and prints each
 *       parsed field to the console.</li>
 * </ol>
 *
 * Run with:
 * <pre>
 *   .\mvnw.cmd compile exec:java -Dexec.mainClass="com.prerequix.demo.DemoRunner"
 * </pre>
 */
public class DemoRunner {

    static final String SEP = "─".repeat(72);
    static final String DBURL;

    static {
        // Resolve the same path DatabaseManager uses
        java.nio.file.Path p = java.nio.file.Paths.get(
                System.getProperty("user.dir"), "prerequix.db");
        DBURL = "jdbc:sqlite:" + p.toAbsolutePath();
    }

    public static void main(String[] args) throws Exception {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║          PreRequix  –  Database & Networking Demo Runner             ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        demoDatabaseSection();
        System.out.println();
        demoNetworkingSection();

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                       Demo complete.                                ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
    }

    // ─── 1.  DATABASE DEMO ────────────────────────────────────────────────

    static void demoDatabaseSection() throws Exception {
        System.out.println("━━━  PART 1 :  SQLite Database  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("DB file : " + DBURL.replace("jdbc:sqlite:", ""));
        System.out.println();

        // --- Create schema ---
        DatabaseManager.getInstance().initialize();

        // --- Seed three sample courses ---
        seedDatabase();

        // --- Print courses table ---
        System.out.println("  TABLE: courses");
        System.out.println("  " + SEP);
        System.out.printf("  %-10s  %-10s  %-34s  %7s  %-14s%n",
                "id", "code", "title", "credits", "status");
        System.out.println("  " + SEP);

        try (Connection conn = DriverManager.getConnection(DBURL);
             Statement  st   = conn.createStatement();
             ResultSet  rs   = st.executeQuery("SELECT * FROM courses ORDER BY code")) {
            while (rs.next()) {
                System.out.printf("  %-10s  %-10s  %-34s  %7.1f  %-14s%n",
                        rs.getString("id"),
                        rs.getString("code"),
                        rs.getString("title"),
                        rs.getDouble("credits"),
                        rs.getString("status"));
            }
        }

        System.out.println("  " + SEP);
        System.out.println();

        // --- Print prerequisites table ---
        System.out.println("  TABLE: prerequisites  (junction table – course → prerequisite)");
        System.out.println("  " + SEP);
        System.out.printf("  %-20s  %-20s%n", "course_id", "prerequisite_id");
        System.out.println("  " + SEP);

        try (Connection conn = DriverManager.getConnection(DBURL);
             Statement  st   = conn.createStatement();
             ResultSet  rs   = st.executeQuery(
                     "SELECT p.course_id, p.prerequisite_id, " +
                     "c1.code AS course_code, c2.code AS prereq_code " +
                     "FROM prerequisites p " +
                     "JOIN courses c1 ON c1.id = p.course_id " +
                     "JOIN courses c2 ON c2.id = p.prerequisite_id " +
                     "ORDER BY p.course_id")) {
            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.printf("  %-20s  %-20s   (%s → %s)%n",
                        rs.getString("course_id"),
                        rs.getString("prerequisite_id"),
                        rs.getString("course_code"),
                        rs.getString("prereq_code"));
            }
            if (!any) System.out.println("  (no prerequisites yet — add courses via the app)");
        }
        System.out.println("  " + SEP);

        // --- CRUD round-trip demo ---
        System.out.println();
        System.out.println("  CRUD round-trip demo:");
        try (Connection conn = DriverManager.getConnection(DBURL);
             Statement  st   = conn.createStatement()) {

            // UPDATE
            st.executeUpdate(
                    "UPDATE courses SET status = 'COMPLETED' WHERE code = 'CS 101'");
            System.out.println("  [UPDATE] CS 101 status -> COMPLETED");

            // READ back
            ResultSet rs = st.executeQuery(
                    "SELECT code, status FROM courses WHERE code = 'CS 101'");
            if (rs.next())
                System.out.println("  [READ]   " + rs.getString("code") +
                                   " status = " + rs.getString("status"));

            // DELETE
            st.executeUpdate("DELETE FROM courses WHERE code = 'DEMO 999'");
            System.out.println("  [DELETE] DEMO 999 removed (if it existed)");
        }
    }

    static void seedDatabase() throws Exception {
        var repo = DatabaseManager.getInstance().repository();

        // Clear old demo rows first so the demo is idempotent
        try (Connection conn = DriverManager.getConnection(DBURL);
             Statement  st   = conn.createStatement()) {
            st.executeUpdate("DELETE FROM prerequisites WHERE course_id IN ('CS101','CS102','MATH101')");
            st.executeUpdate("DELETE FROM courses WHERE id IN ('CS101','CS102','MATH101')");
        }

        Course cs101  = new Course("CS101",  "CS 101",   "Introduction to Programming", "Fundamentals of Java", 3.0, "Computer Science");
        Course cs102  = new Course("CS102",  "CS 102",   "Object-Oriented Programming",  "Classes and OOP",     3.0, "Computer Science");
        Course math101= new Course("MATH101","MATH 101", "Discrete Mathematics",         "Logic and sets",      3.0, "Mathematics");

        cs101.setStatus(CourseStatus.COMPLETED);
        cs102.addPrerequisite("CS101");

        repo.saveCourse(cs101);
        repo.saveCourse(cs102);
        repo.saveCourse(math101);
        repo.savePrerequisites("CS102", cs102.getPrerequisiteIds());

        System.out.println("  [CREATE] Seeded 3 courses (CS101, CS102, MATH101) into prerequix.db");
        System.out.println();
    }

    // ─── 2.  NETWORKING + JSON PARSING DEMO ──────────────────────────────

    static void demoNetworkingSection() throws Exception {
        System.out.println("━━━  PART 2 :  HTTP Request + JSON Parsing  ━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();
        System.out.println("  Step 1 – Sending HTTP GET request ...");
        System.out.println("  URL: " + CourseApiClient.SAMPLE_COURSES_URL);
        System.out.println();

        long start = System.currentTimeMillis();
        CourseApiClient client = new CourseApiClient();
        String rawJson;
        try {
            rawJson = client.fetchSampleCourses();
        } catch (Exception ex) {
            System.out.println("  [ERROR] Network unavailable: " + ex.getMessage());
            System.out.println("  (Make sure you are connected to the internet)");
            return;
        }
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("  HTTP 200 OK  (" + elapsed + " ms)  |  " +
                           rawJson.length() + " bytes received");
        System.out.println();

        // Print first 300 chars of raw JSON
        System.out.println("  Step 2 – Raw JSON response (first 300 chars):");
        System.out.println("  " + SEP);
        System.out.println("  " + rawJson.substring(0, Math.min(300, rawJson.length()))
                                         .replace("\n", "\n  ") + " ...");
        System.out.println("  " + SEP);
        System.out.println();

        // Parse
        System.out.println("  Step 3 – Parsing JSON with RemoteCourseParser (org.json):");
        System.out.println();
        RemoteCourseParser parser = new RemoteCourseParser();
        String sourceInfo = parser.parseSourceInfo(rawJson);
        System.out.println("  Source : " + sourceInfo);
        System.out.println();

        List<Course> courses = parser.parse(rawJson);
        System.out.printf("  %-10s  %-10s  %-36s  %7s  %-6s%n",
                "id", "code", "title", "credits", "dept");
        System.out.println("  " + SEP);
        for (Course c : courses) {
            System.out.printf("  %-10s  %-10s  %-36s  %7.1f  %-6s%n",
                    c.getId(), c.getCode(), c.getTitle(), c.getCredits(),
                    c.getDepartment().length() > 6
                            ? c.getDepartment().substring(0, 6)
                            : c.getDepartment());
        }
        System.out.println("  " + SEP);
        System.out.println("  Parsed " + courses.size() + " courses from remote JSON.");
        System.out.println();
        System.out.println("  -> These courses are what 'Import from Web' inserts into the DB.");
    }
}
