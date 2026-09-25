package com.prerequix.net;

import com.prerequix.model.Course;
import com.prerequix.model.CourseStatus;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses the JSON payload returned by {@link CourseApiClient} into a list of
 * {@link Course} objects.
 *
 * <p><b>Expected JSON structure:</b>
 * <pre>
 * {
 *   "source": "...",
 *   "version": "1.0",
 *   "courses": [
 *     {
 *       "code": "CS 101",
 *       "title": "Intro to Computer Science",
 *       "credits": 3.0,
 *       "department": "Computer Science",
 *       "description": "...",
 *       "prerequisites": ["CS 100"]   // optional; codes of prerequisite courses
 *     },
 *     ...
 *   ]
 * }
 * </pre>
 *
 * <p><b>Networking & Data Parsing requirement:</b>
 * This class demonstrates JSON parsing using the {@code org.json} library.
 * Prerequisite codes in the JSON are stored as temporary raw IDs; the caller
 * must resolve them to proper course IDs after all courses are imported.
 */
public class RemoteCourseParser {

    /**
     * Parses a raw JSON string into a list of {@link Course} objects.
     *
     * @param json the raw JSON body (UTF-8 string)
     * @return parsed list of courses; never {@code null}
     * @throws org.json.JSONException if the JSON is malformed or missing required fields
     */
    public List<Course> parse(String json) {
        List<Course> courses = new ArrayList<>();

        JSONObject root = new JSONObject(json);
        JSONArray arr  = root.getJSONArray("courses");

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);

            String code    = obj.optString("code",       "UNKNOWN");
            String title   = obj.optString("title",      "Untitled");
            double credits = obj.optDouble("credits",    3.0);
            String dept    = obj.optString("department", "General");
            String desc    = obj.optString("description","");
            String status  = obj.optString("status",     "UNCOMPLETED");

            Course c = new Course(
                    Course.sanitizeId(code),  // id derived from code
                    code,
                    title,
                    desc,
                    credits,
                    dept
            );
            c.setStatus(parseStatus(status));

            // Prerequisites are stored as code strings in the JSON;
            // we store them as-is and rely on the import dialog to resolve IDs.
            if (obj.has("prerequisites")) {
                JSONArray prereqs = obj.getJSONArray("prerequisites");
                Set<String> prereqCodes = new HashSet<>();
                for (int j = 0; j < prereqs.length(); j++) {
                    prereqCodes.add(prereqs.getString(j));
                }
                // Temporarily store codes in the prerequisite ID set;
                // ImportCoursesDialog converts them to IDs after inserting all courses.
                c.getPrerequisiteIds().addAll(prereqCodes);
            }

            courses.add(c);
        }

        return courses;
    }

    /**
     * Returns metadata from the JSON root object (source name, version).
     * Used by the import dialog to show the origin of the data.
     */
    public String parseSourceInfo(String json) {
        JSONObject root = new JSONObject(json);
        String source  = root.optString("source",  "Unknown source");
        String version = root.optString("version", "?");
        int    count   = root.getJSONArray("courses").length();
        return source + " v" + version + " (" + count + " courses)";
    }

    private CourseStatus parseStatus(String raw) {
        return switch (raw.toUpperCase()) {
            case "COMPLETED"   -> CourseStatus.COMPLETED;
            case "IN_PROGRESS" -> CourseStatus.IN_PROGRESS;
            default            -> CourseStatus.UNCOMPLETED;
        };
    }
}
