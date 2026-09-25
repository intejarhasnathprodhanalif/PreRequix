package com.prerequix.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Directed Graph data structure representing courses as vertices and prerequisite relationships as directed edges.
 * Edge A -> B means course A is a prerequisite for course B.
 */
public class CourseGraph {
    private final Map<String, Course> coursesMap = new LinkedHashMap<>();

    public CourseGraph() {
    }

    public synchronized void clear() {
        coursesMap.clear();
    }

    public synchronized void addCourse(Course course) {
        if (course == null || course.getId() == null || course.getId().isBlank()) {
            throw new IllegalArgumentException("Course ID cannot be null or empty.");
        }
        coursesMap.put(course.getId(), course);
    }

    public synchronized void removeCourse(String courseId) {
        String sanitized = Course.sanitizeId(courseId);
        if (!coursesMap.containsKey(sanitized)) return;

        // Remove from map
        coursesMap.remove(sanitized);

        // Remove references from all other courses' prerequisites
        for (Course c : coursesMap.values()) {
            c.removePrerequisite(sanitized);
        }
    }

    public synchronized Course getCourse(String courseId) {
        return coursesMap.get(Course.sanitizeId(courseId));
    }

    public synchronized Collection<Course> getAllCourses() {
        return Collections.unmodifiableCollection(coursesMap.values());
    }

    public synchronized boolean hasCourse(String courseId) {
        return coursesMap.containsKey(Course.sanitizeId(courseId));
    }

    public synchronized void addPrerequisite(String targetCourseId, String prereqCourseId) {
        String targetId = Course.sanitizeId(targetCourseId);
        String prereqId = Course.sanitizeId(prereqCourseId);

        if (targetId.equals(prereqId)) {
            throw new IllegalArgumentException("A course cannot be a prerequisite of itself.");
        }

        Course target = coursesMap.get(targetId);
        Course prereq = coursesMap.get(prereqId);

        if (target == null || prereq == null) {
            throw new IllegalArgumentException("Both target course and prerequisite course must exist in the graph.");
        }

        target.addPrerequisite(prereqId);
    }

    public synchronized void removePrerequisite(String targetCourseId, String prereqCourseId) {
        String targetId = Course.sanitizeId(targetCourseId);
        Course target = coursesMap.get(targetId);
        if (target != null) {
            target.removePrerequisite(prereqCourseId);
        }
    }

    // --- Direct and Indirect Prerequisites Queries ---

    public synchronized Set<Course> getDirectPrerequisites(String courseId) {
        Course course = getCourse(courseId);
        if (course == null) return Collections.emptySet();

        Set<Course> result = new LinkedHashSet<>();
        for (String prereqId : course.getPrerequisiteIds()) {
            Course prereq = coursesMap.get(prereqId);
            if (prereq != null) {
                result.add(prereq);
            }
        }
        return result;
    }

    /**
     * Finds all prerequisites (transitive closure) for a target course.
     */
    public synchronized Set<Course> getAllPrerequisites(String courseId) {
        Course course = getCourse(courseId);
        if (course == null) return Collections.emptySet();

        Set<Course> allPrereqs = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>(course.getPrerequisiteIds());

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            Course current = coursesMap.get(currentId);
            if (current != null && !allPrereqs.contains(current)) {
                allPrereqs.add(current);
                queue.addAll(current.getPrerequisiteIds());
            }
        }
        return allPrereqs;
    }

    /**
     * Gets indirect prerequisites (all prerequisites minus direct ones).
     */
    public synchronized Set<Course> getIndirectPrerequisites(String courseId) {
        Set<Course> all = getAllPrerequisites(courseId);
        Set<Course> direct = getDirectPrerequisites(courseId);
        all.removeAll(direct);
        return all;
    }

    /**
     * Gets courses that directly depend on the given course.
     */
    public synchronized Set<Course> getDirectDependents(String courseId) {
        String sanitized = Course.sanitizeId(courseId);
        Set<Course> dependents = new LinkedHashSet<>();
        for (Course c : coursesMap.values()) {
            if (c.getPrerequisiteIds().contains(sanitized)) {
                dependents.add(c);
            }
        }
        return dependents;
    }

    /**
     * Gets all courses that transitively depend on the given course.
     */
    public synchronized Set<Course> getAllDependents(String courseId) {
        String sanitized = Course.sanitizeId(courseId);
        Set<Course> allDependents = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>();

        for (Course c : coursesMap.values()) {
            if (c.getPrerequisiteIds().contains(sanitized)) {
                queue.add(c.getId());
            }
        }

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            Course current = coursesMap.get(currentId);
            if (current != null && !allDependents.contains(current)) {
                allDependents.add(current);
                for (Course next : getDirectDependents(current.getId())) {
                    queue.add(next.getId());
                }
            }
        }
        return allDependents;
    }

    // --- Status & Availability ---

    public synchronized boolean isCourseAvailable(String courseId) {
        Course course = getCourse(courseId);
        if (course == null || course.isCompleted()) return false;

        for (String prereqId : course.getPrerequisiteIds()) {
            Course prereq = coursesMap.get(prereqId);
            if (prereq == null || !prereq.isCompleted()) {
                return false;
            }
        }
        return true;
    }

    public synchronized List<Course> getAvailableCourses() {
        return coursesMap.values().stream()
                .filter(c -> !c.isCompleted() && isCourseAvailable(c.getId()))
                .collect(Collectors.toList());
    }

    public synchronized List<Course> getCompletedCourses() {
        return coursesMap.values().stream()
                .filter(Course::isCompleted)
                .collect(Collectors.toList());
    }

    public synchronized List<Course> getInProgressCourses() {
        return coursesMap.values().stream()
                .filter(Course::isInProgress)
                .collect(Collectors.toList());
    }

    // --- Cycle Detection Algorithms ---

    /**
     * Detects if there is any circular prerequisite dependency in the graph.
     * @return List of course IDs representing the cycle (e.g. [A, B, C, A]), or empty list if no cycle.
     */
    public synchronized List<String> detectCycle() {
        Map<String, Integer> state = new HashMap<>(); // 0: unvisited, 1: visiting, 2: visited
        Map<String, String> parent = new HashMap<>();

        for (String node : coursesMap.keySet()) {
            state.put(node, 0);
        }

        for (String node : coursesMap.keySet()) {
            if (state.get(node) == 0) {
                List<String> cycle = dfsCycle(node, state, parent);
                if (!cycle.isEmpty()) {
                    return cycle;
                }
            }
        }
        return Collections.emptyList();
    }

    private List<String> dfsCycle(String u, Map<String, Integer> state, Map<String, String> parent) {
        state.put(u, 1);
        Course course = coursesMap.get(u);

        if (course != null) {
            for (String v : course.getPrerequisiteIds()) {
                if (!coursesMap.containsKey(v)) continue;

                if (state.get(v) == 1) { // Found back-edge (cycle)
                    List<String> cyclePath = new ArrayList<>();
                    cyclePath.add(v);
                    String curr = u;
                    while (curr != null && !curr.equals(v)) {
                        cyclePath.add(curr);
                        curr = parent.get(curr);
                    }
                    cyclePath.add(v);
                    Collections.reverse(cyclePath);
                    return cyclePath;
                } else if (state.get(v) == 0) {
                    parent.put(v, u);
                    List<String> cycle = dfsCycle(v, state, parent);
                    if (!cycle.isEmpty()) {
                        return cycle;
                    }
                }
            }
        }

        state.put(u, 2);
        return Collections.emptyList();
    }

    /**
     * Checks if adding a proposed prerequisite edge (prereq -> target) would introduce a circular dependency.
     */
    public synchronized boolean wouldCauseCycle(String targetCourseId, String proposedPrereqId) {
        String targetId = Course.sanitizeId(targetCourseId);
        String prereqId = Course.sanitizeId(proposedPrereqId);

        if (targetId.equals(prereqId)) return true;

        // If targetId is already a prerequisite (direct or indirect) of proposedPrereqId,
        // then adding proposedPrereqId as prerequisite of targetId would create a cycle!
        Set<Course> prereqsOfProposed = getAllPrerequisites(prereqId);
        return prereqsOfProposed.stream().anyMatch(c -> c.getId().equals(targetId));
    }

    // --- Topological Sort & Semester Sequence Generation ---

    /**
     * Generates a valid term-by-term course-taking schedule based on prerequisite dependencies and max credit limit per term.
     */
    public synchronized List<SemesterPlan> generateSemesterPlan(double maxCreditsPerTerm) {
        if (!detectCycle().isEmpty()) {
            throw new IllegalStateException("Cannot generate schedule: Graph contains circular prerequisite dependencies!");
        }

        List<SemesterPlan> schedule = new ArrayList<>();

        // Create temporary copy of course completion state and required remaining prerequisites
        Map<String, Set<String>> remainingPrereqs = new HashMap<>();
        Set<String> completedIds = new HashSet<>();

        for (Course c : coursesMap.values()) {
            if (c.isCompleted()) {
                completedIds.add(c.getId());
            } else {
                remainingPrereqs.put(c.getId(), new HashSet<>(c.getPrerequisiteIds()));
            }
        }

        // Clean up remaining prereqs by removing already completed ones
        for (Set<String> prereqs : remainingPrereqs.values()) {
            prereqs.removeIf(completedIds::contains);
        }

        int termCounter = 1;

        while (!remainingPrereqs.isEmpty()) {
            SemesterPlan currentTerm = new SemesterPlan(termCounter);

            // Find all courses eligible for this term (0 remaining uncompleted prerequisites)
            List<Course> eligible = remainingPrereqs.entrySet().stream()
                    .filter(entry -> entry.getValue().isEmpty())
                    .map(entry -> coursesMap.get(entry.getKey()))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Course::getCode))
                    .collect(Collectors.toList());

            if (eligible.isEmpty()) {
                // Should not happen unless there's an unresolved dependency to an unadded course ID
                break;
            }

            List<String> scheduledThisTerm = new ArrayList<>();

            for (Course course : eligible) {
                if (currentTerm.canFitCourse(course, maxCreditsPerTerm)) {
                    currentTerm.addCourse(course);
                    scheduledThisTerm.add(course.getId());
                }
            }

            if (scheduledThisTerm.isEmpty()) {
                // Force scheduled single course if max credit limit is lower than course credits
                Course single = eligible.get(0);
                currentTerm.addCourse(single);
                scheduledThisTerm.add(single.getId());
            }

            // Remove scheduled courses from remaining map and mark as completed for subsequent terms
            for (String id : scheduledThisTerm) {
                remainingPrereqs.remove(id);
                completedIds.add(id);
            }

            // Update remaining prerequisite requirements for un-scheduled courses
            for (Set<String> prereqs : remainingPrereqs.values()) {
                prereqs.removeIf(scheduledThisTerm::contains);
            }

            schedule.add(currentTerm);
            termCounter++;
        }

        return schedule;
    }

    /**
     * Generates a structured academic plan subject to two hard constraints:
     * <ol>
     *   <li>Each term contains <b>exactly {@code coursesPerTerm}</b> courses
     *       (the last term may have fewer if the remaining eligible courses run out).</li>
     *   <li>The running total of credits across ALL terms must <b>not exceed
     *       {@code maxTotalCredits}</b>.  Any course that would push the total over
     *       this limit is placed in the {@link AcademicPlanResult#excludedCourses} list
     *       and omitted from the plan.</li>
     * </ol>
     *
     * <p>Prerequisite ordering is always respected: a course is only eligible for a
     * term after all its prerequisites have been placed in earlier terms (or are
     * already completed).</p>
     *
     * @param coursesPerTerm  target number of courses per term (e.g. 5)
     * @param maxTotalCredits hard cap on cumulative credits (e.g. 120)
     * @return an {@link AcademicPlanResult} with the planned terms and any excluded courses
     * @throws IllegalStateException if the graph contains a circular dependency
     */
    public synchronized AcademicPlanResult generateAcademicPlan(int coursesPerTerm,
                                                                  double maxTotalCredits) {
        if (!detectCycle().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot generate academic plan: circular prerequisite dependencies detected!");
        }

        List<SemesterPlan> schedule        = new ArrayList<>();
        List<Course>       excluded        = new ArrayList<>();

        // Track which uncompleted courses still need to be scheduled
        Map<String, Set<String>> remainingPrereqs = new HashMap<>();
        Set<String>              completedIds     = new HashSet<>();

        for (Course c : coursesMap.values()) {
            if (c.isCompleted()) {
                completedIds.add(c.getId());
            } else {
                remainingPrereqs.put(c.getId(), new HashSet<>(c.getPrerequisiteIds()));
            }
        }
        // Strip already-completed prerequisites
        for (Set<String> prereqs : remainingPrereqs.values()) {
            prereqs.removeIf(completedIds::contains);
        }

        double totalCredits = 0.0;
        int    termNumber   = 1;

        while (!remainingPrereqs.isEmpty()) {
            // Courses with no outstanding prerequisites — ready to take this term
            List<Course> eligible = remainingPrereqs.entrySet().stream()
                    .filter(e -> e.getValue().isEmpty())
                    .map(e -> coursesMap.get(e.getKey()))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Course::getCode))
                    .collect(Collectors.toList());

            if (eligible.isEmpty()) break; // Shouldn't happen in a valid DAG

            SemesterPlan   term        = new SemesterPlan(termNumber);
            List<String>   addedThisTerm = new ArrayList<>();
            List<Course>   skippedForCredit = new ArrayList<>();

            for (Course course : eligible) {
                if (addedThisTerm.size() >= coursesPerTerm) {
                    // Term is full — the rest stay for a later term
                    break;
                }
                double newTotal = totalCredits + course.getCredits();
                if (newTotal > maxTotalCredits) {
                    // Adding this course would exceed the credit cap — exclude it
                    skippedForCredit.add(course);
                } else {
                    term.addCourse(course);
                    addedThisTerm.add(course.getId());
                    totalCredits = newTotal;
                }
            }

            // All eligible courses exceeded the credit cap — plan is complete
            if (addedThisTerm.isEmpty()) {
                excluded.addAll(skippedForCredit);
                // Mark remaining courses as excluded
                remainingPrereqs.keySet().stream()
                        .map(coursesMap::get)
                        .filter(Objects::nonNull)
                        .filter(c -> !skippedForCredit.contains(c))
                        .forEach(excluded::add);
                break;
            }

            // Remove scheduled courses and update downstream dependencies
            for (String id : addedThisTerm) {
                remainingPrereqs.remove(id);
                completedIds.add(id);
            }
            for (Set<String> prereqs : remainingPrereqs.values()) {
                prereqs.removeIf(addedThisTerm::contains);
            }

            schedule.add(term);
            termNumber++;
        }

        // Any courses still in remainingPrereqs whose prerequisites were only just
        // satisfied this term but never got scheduled are excluded (credit cap reached)
        remainingPrereqs.keySet().stream()
                .map(coursesMap::get)
                .filter(Objects::nonNull)
                .filter(c -> !excluded.contains(c))
                .sorted(Comparator.comparing(Course::getCode))
                .forEach(excluded::add);

        return new AcademicPlanResult(schedule, excluded);
    }

    /**
     * Computes maximum prerequisite depth for visual graph tier placement.
     */
    public synchronized int getPrerequisiteDepth(String courseId) {
        Course course = getCourse(courseId);
        if (course == null || course.getPrerequisiteIds().isEmpty()) {
            return 0;
        }

        int maxDepth = 0;
        for (String prereqId : course.getPrerequisiteIds()) {
            maxDepth = Math.max(maxDepth, 1 + getPrerequisiteDepth(prereqId));
        }
        return maxDepth;
    }
}
