package com.prerequix.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class CourseGraphTest {

    private CourseGraph graph;

    @BeforeEach
    public void setUp() {
        graph = new CourseGraph();
    }

    @Test
    @DisplayName("Test direct and indirect prerequisites resolution")
    public void testDirectAndIndirectPrerequisites() {
        Course cs101 = new Course("CS101", "CS 101", "Intro to CS", "Desc", 3.0, "CS");
        Course cs102 = new Course("CS102", "CS 102", "OOP", "Desc", 3.0, "CS");
        Course cs201 = new Course("CS201", "CS 201", "Data Structures", "Desc", 4.0, "CS");
        Course cs301 = new Course("CS301", "CS 301", "Algorithms", "Desc", 3.0, "CS");

        cs102.addPrerequisite("CS101");
        cs201.addPrerequisite("CS102");
        cs301.addPrerequisite("CS201");

        graph.addCourse(cs101);
        graph.addCourse(cs102);
        graph.addCourse(cs201);
        graph.addCourse(cs301);

        Set<Course> directOfCs301 = graph.getDirectPrerequisites("CS301");
        assertEquals(1, directOfCs301.size());
        assertTrue(directOfCs301.contains(cs201));

        Set<Course> allPrereqsOfCs301 = graph.getAllPrerequisites("CS301");
        assertEquals(3, allPrereqsOfCs301.size());
        assertTrue(allPrereqsOfCs301.contains(cs201));
        assertTrue(allPrereqsOfCs301.contains(cs102));
        assertTrue(allPrereqsOfCs301.contains(cs101));

        Set<Course> indirectOfCs301 = graph.getIndirectPrerequisites("CS301");
        assertEquals(2, indirectOfCs301.size());
        assertTrue(indirectOfCs301.contains(cs102));
        assertTrue(indirectOfCs301.contains(cs101));
        assertFalse(indirectOfCs301.contains(cs201));
    }

    @Test
    @DisplayName("Test circular dependency cycle detection")
    public void testCycleDetection() {
        Course a = new Course("A", "COURSE A", "Course A", "Desc", 3.0, "CS");
        Course b = new Course("B", "COURSE B", "Course B", "Desc", 3.0, "CS");
        Course c = new Course("C", "COURSE C", "Course C", "Desc", 3.0, "CS");

        // Cycle: A -> B -> C -> A
        b.addPrerequisite("A");
        c.addPrerequisite("B");
        a.addPrerequisite("C");

        graph.addCourse(a);
        graph.addCourse(b);
        graph.addCourse(c);

        List<String> cycle = graph.detectCycle();
        assertFalse(cycle.isEmpty(), "Cycle should be detected");
        assertTrue(cycle.size() >= 3, "Cycle path should contain participating courses");
    }

    @Test
    @DisplayName("Test wouldCauseCycle pre-validation")
    public void testWouldCauseCycle() {
        Course a = new Course("A", "COURSE A", "Course A", "Desc", 3.0, "CS");
        Course b = new Course("B", "COURSE B", "Course B", "Desc", 3.0, "CS");
        b.addPrerequisite("A");

        graph.addCourse(a);
        graph.addCourse(b);

        // Adding A as prerequisite to B is valid (A -> B)
        assertFalse(graph.wouldCauseCycle("B", "A"));

        // Adding B as prerequisite to A would create a cycle (A -> B -> A)
        assertTrue(graph.wouldCauseCycle("A", "B"));
    }

    @Test
    @DisplayName("Test available unlocked courses tracking")
    public void testAvailableCourses() {
        Course math101 = new Course("MATH101", "MATH 101", "Calculus I", "Desc", 4.0, "Math", CourseStatus.COMPLETED);
        Course cs101 = new Course("CS101", "CS 101", "Intro CS", "Desc", 3.0, "CS", CourseStatus.COMPLETED);
        Course cs102 = new Course("CS102", "CS 102", "OOP", "Desc", 3.0, "CS", CourseStatus.UNCOMPLETED);
        cs102.addPrerequisite("CS101");

        Course cs201 = new Course("CS201", "CS 201", "Data Structures", "Desc", 4.0, "CS", CourseStatus.UNCOMPLETED);
        cs201.addPrerequisite("CS102");
        cs201.addPrerequisite("MATH101");

        graph.addCourse(math101);
        graph.addCourse(cs101);
        graph.addCourse(cs102);
        graph.addCourse(cs201);

        assertTrue(graph.isCourseAvailable("CS102"), "CS102 should be available because CS101 is completed");
        assertFalse(graph.isCourseAvailable("CS201"), "CS201 should not be available because CS102 is uncompleted");

        List<Course> available = graph.getAvailableCourses();
        assertEquals(1, available.size());
        assertEquals("CS102", available.get(0).getId());
    }

    @Test
    @DisplayName("Test semester plan sequence generation with credit limit")
    public void testSemesterSequenceGeneration() {
        Course c1 = new Course("C1", "C 1", "Course 1", "Desc", 4.0, "CS", CourseStatus.UNCOMPLETED);
        Course c2 = new Course("C2", "C 2", "Course 2", "Desc", 4.0, "CS", CourseStatus.UNCOMPLETED);
        Course c3 = new Course("C3", "C 3", "Course 3", "Desc", 4.0, "CS", CourseStatus.UNCOMPLETED);

        c2.addPrerequisite("C1");
        c3.addPrerequisite("C2");

        graph.addCourse(c1);
        graph.addCourse(c2);
        graph.addCourse(c3);

        List<SemesterPlan> plan = graph.generateSemesterPlan(12.0);
        assertEquals(3, plan.size(), "Should take 3 terms due to prerequisite chain C1 -> C2 -> C3");
        assertEquals(1, plan.get(0).getCourses().size());
        assertEquals("C1", plan.get(0).getCourses().get(0).getId());
        assertEquals("C2", plan.get(1).getCourses().get(0).getId());
        assertEquals("C3", plan.get(2).getCourses().get(0).getId());
    }
}
