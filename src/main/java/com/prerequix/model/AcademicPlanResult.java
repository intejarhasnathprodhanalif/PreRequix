package com.prerequix.model;

import java.util.List;

/**
 * Result of {@link CourseGraph#generateAcademicPlan}.
 *
 * <p>Carries two outputs:
 * <ul>
 *   <li>{@link #plannedTerms}  – terms that fit within the academic constraints.</li>
 *   <li>{@link #excludedCourses} – courses that were valid to take but could not be
 *       included because adding them would exceed the 120-credit total cap.</li>
 * </ul>
 */
public class AcademicPlanResult {

    /** The ordered list of semester plans that satisfy all constraints. */
    public final List<SemesterPlan> plannedTerms;

    /**
     * Courses that topologically could be taken but were excluded because the
     * total-credit cap ({@code maxTotalCredits}) would have been exceeded.
     */
    public final List<Course> excludedCourses;

    /** Running total of credits across all planned terms. */
    public final double totalPlannedCredits;

    /** Number of terms in the plan. */
    public final int totalTerms;

    public AcademicPlanResult(List<SemesterPlan> plannedTerms,
                               List<Course> excludedCourses) {
        this.plannedTerms    = plannedTerms;
        this.excludedCourses = excludedCourses;
        this.totalPlannedCredits = plannedTerms.stream()
                .mapToDouble(SemesterPlan::getTotalCredits)
                .sum();
        this.totalTerms = plannedTerms.size();
    }
}
