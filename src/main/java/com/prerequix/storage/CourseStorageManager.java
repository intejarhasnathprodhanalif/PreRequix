package com.prerequix.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import com.prerequix.model.CourseStatus;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles JSON persistence and preset sample curricula initialization.
 */
public class CourseStorageManager {
    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path storagePath;

    public CourseStorageManager() {
        String userHome = System.getProperty("user.home", ".");
        this.storagePath = Paths.get(userHome, ".prerequix", "courses.json");
    }

    public CourseStorageManager(Path customPath) {
        this.storagePath = customPath;
    }

    public Path getStoragePath() {
        return storagePath;
    }

    public void saveGraph(CourseGraph graph) throws IOException {
        File file = storagePath.toFile();
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        List<Course> coursesList = new ArrayList<>(graph.getAllCourses());
        mapper.writeValue(file, coursesList);
    }

    public boolean loadGraph(CourseGraph graph) {
        File file = storagePath.toFile();
        if (!file.exists() || file.length() == 0) {
            return false;
        }

        try {
            List<Course> coursesList = mapper.readValue(file, new TypeReference<List<Course>>() {});
            graph.clear();
            for (Course c : coursesList) {
                graph.addCourse(c);
            }
            return true;
        } catch (Exception e) {
            System.err.println("Failed to load saved courses from JSON: " + e.getMessage());
            return false;
        }
    }

    public void loadComputerSciencePreset(CourseGraph graph) {
        graph.clear();

        Course math101 = new Course("MATH101", "MATH 101", "Calculus I", "Limits, derivatives, integrals, and applications.", 4.0, "Mathematics", CourseStatus.COMPLETED);
        Course math102 = new Course("MATH102", "MATH 102", "Calculus II", "Integration techniques, series, multivariable calculus.", 4.0, "Mathematics", CourseStatus.COMPLETED);
        math102.addPrerequisite("MATH101");

        Course cs101 = new Course("CS101", "CS 101", "Intro to Computer Science", "Fundamental programming concepts, logic, and problem solving in Java.", 3.0, "Computer Science", CourseStatus.COMPLETED);
        Course cs102 = new Course("CS102", "CS 102", "Object Oriented Programming", "Classes, inheritance, polymorphism, design patterns, and JavaFX basics.", 3.0, "Computer Science", CourseStatus.IN_PROGRESS);
        cs102.addPrerequisite("CS101");

        Course cs201 = new Course("CS201", "CS 201", "Data Structures", "Arrays, Linked Lists, Trees, Graphs, Hash Tables, and Memory Complexity.", 4.0, "Computer Science", CourseStatus.UNCOMPLETED);
        cs201.addPrerequisite("CS102");
        cs201.addPrerequisite("MATH101");

        Course cs202 = new Course("CS202", "CS 202", "Computer Architecture", "Digital logic, CPU design, assembly language, memory hierarchy.", 3.0, "Computer Science", CourseStatus.UNCOMPLETED);
        cs202.addPrerequisite("CS101");

        Course cs301 = new Course("CS301", "CS 301", "Design & Analysis of Algorithms", "Greedy algorithms, dynamic programming, graph algorithms, P vs NP.", 3.0, "Computer Science", CourseStatus.UNCOMPLETED);
        cs301.addPrerequisite("CS201");
        cs301.addPrerequisite("MATH102");

        Course cs305 = new Course("CS305", "CS 305", "Operating Systems", "Processes, threads, concurrency, deadlocks, file systems.", 3.0, "Computer Science", CourseStatus.UNCOMPLETED);
        cs305.addPrerequisite("CS201");
        cs305.addPrerequisite("CS202");

        Course cs310 = new Course("CS310", "CS 310", "Database Systems", "Relational algebra, SQL, normalization, transactions, indexing.", 3.0, "Computer Science", CourseStatus.UNCOMPLETED);
        cs310.addPrerequisite("CS201");

        Course cs401 = new Course("CS401", "CS 401", "Artificial Intelligence", "Search algorithms, knowledge representation, machine learning basics.", 3.0, "Computer Science", CourseStatus.UNCOMPLETED);
        cs401.addPrerequisite("CS301");

        Course cs490 = new Course("CS490", "CS 490", "Senior Capstone Project", "Comprehensive team software engineering capstone project.", 4.0, "Computer Science", CourseStatus.UNCOMPLETED);
        cs490.addPrerequisite("CS305");
        cs490.addPrerequisite("CS310");

        graph.addCourse(math101);
        graph.addCourse(math102);
        graph.addCourse(cs101);
        graph.addCourse(cs102);
        graph.addCourse(cs201);
        graph.addCourse(cs202);
        graph.addCourse(cs301);
        graph.addCourse(cs305);
        graph.addCourse(cs310);
        graph.addCourse(cs401);
        graph.addCourse(cs490);
    }

    public void loadElectricalEngineeringPreset(CourseGraph graph) {
        graph.clear();

        Course math101 = new Course("MATH101", "MATH 101", "Calculus I", "Limits, derivatives, integrals.", 4.0, "Mathematics", CourseStatus.COMPLETED);
        Course phys101 = new Course("PHYS101", "PHYS 101", "University Physics I", "Mechanics, kinematics, energy, momentum.", 4.0, "Physics", CourseStatus.COMPLETED);

        Course math102 = new Course("MATH102", "MATH 102", "Calculus II", "Techniques of integration and differential equations.", 4.0, "Mathematics", CourseStatus.IN_PROGRESS);
        math102.addPrerequisite("MATH101");

        Course phys102 = new Course("PHYS102", "PHYS 102", "University Physics II", "Electricity, magnetism, circuits, optics.", 4.0, "Physics", CourseStatus.IN_PROGRESS);
        phys102.addPrerequisite("PHYS101");

        Course eee101 = new Course("EEE101", "EEE 101", "Circuit Analysis I", "KCL, KVL, nodal and mesh analysis, op-amps.", 3.0, "Electrical Engineering", CourseStatus.UNCOMPLETED);
        eee101.addPrerequisite("MATH101");
        eee101.addPrerequisite("PHYS101");

        Course eee201 = new Course("EEE201", "EEE 201", "Circuit Analysis II", "AC steady-state, phasors, Laplace transforms.", 3.0, "Electrical Engineering", CourseStatus.UNCOMPLETED);
        eee201.addPrerequisite("EEE101");
        eee201.addPrerequisite("MATH102");

        Course eee205 = new Course("EEE205", "EEE 205", "Digital Logic Design", "Boolean algebra, logic gates, flip-flops, sequential circuits.", 3.0, "Electrical Engineering", CourseStatus.UNCOMPLETED);
        eee205.addPrerequisite("EEE101");

        Course eee301 = new Course("EEE301", "EEE 301", "Signals & Systems", "Continuous and discrete Fourier transforms, z-transforms.", 3.0, "Electrical Engineering", CourseStatus.UNCOMPLETED);
        eee301.addPrerequisite("EEE201");

        Course eee305 = new Course("EEE305", "EEE 305", "Microprocessor Systems", "Microcontroller architecture, Assembly, I/O interfacing.", 3.0, "Electrical Engineering", CourseStatus.UNCOMPLETED);
        eee305.addPrerequisite("EEE205");

        Course eee401 = new Course("EEE401", "EEE 401", "Embedded Systems Capstone", "Real-time operating systems, sensor integration, hardware design.", 4.0, "Electrical Engineering", CourseStatus.UNCOMPLETED);
        eee401.addPrerequisite("EEE301");
        eee401.addPrerequisite("EEE305");

        graph.addCourse(math101);
        graph.addCourse(phys101);
        graph.addCourse(math102);
        graph.addCourse(phys102);
        graph.addCourse(eee101);
        graph.addCourse(eee201);
        graph.addCourse(eee205);
        graph.addCourse(eee301);
        graph.addCourse(eee305);
        graph.addCourse(eee401);
    }

    public void loadBusinessAnalyticsPreset(CourseGraph graph) {
        graph.clear();

        Course stat101 = new Course("STAT101", "STAT 101", "Introductory Statistics", "Probability distributions, hypothesis testing, confidence intervals.", 3.0, "Statistics", CourseStatus.COMPLETED);
        Course econ101 = new Course("ECON101", "ECON 101", "Microeconomics", "Supply and demand, market structure, consumer choice.", 3.0, "Economics", CourseStatus.COMPLETED);
        Course bus101 = new Course("BUS101", "BUS 101", "Foundations of Business", "Business organizations, marketing, operations, finance.", 3.0, "Business", CourseStatus.COMPLETED);
        Course cs101 = new Course("CS101", "CS 101", "Python for Business", "Programming basics, data handling with Pandas and NumPy.", 3.0, "Computer Science", CourseStatus.IN_PROGRESS);

        Course bus201 = new Course("BUS201", "BUS 201", "Business Data Mining", "Data cleaning, exploratory data analysis, predictive models.", 3.0, "Business", CourseStatus.UNCOMPLETED);
        bus201.addPrerequisite("STAT101");
        bus201.addPrerequisite("CS101");

        Course econ201 = new Course("ECON201", "ECON 201", "Econometrics", "Regression analysis, time series forecasting.", 3.0, "Economics", CourseStatus.UNCOMPLETED);
        econ201.addPrerequisite("ECON101");
        econ201.addPrerequisite("STAT101");

        Course bus301 = new Course("BUS301", "BUS 301", "Machine Learning for Business", "Classification, clustering, decision trees, neural nets.", 3.0, "Business", CourseStatus.UNCOMPLETED);
        bus301.addPrerequisite("BUS201");

        Course bus401 = new Course("BUS401", "BUS 401", "Strategic Data Analytics", "Cap-stone decision science case studies for executive decision-making.", 4.0, "Business", CourseStatus.UNCOMPLETED);
        bus401.addPrerequisite("BUS301");
        bus401.addPrerequisite("ECON201");

        graph.addCourse(stat101);
        graph.addCourse(econ101);
        graph.addCourse(bus101);
        graph.addCourse(cs101);
        graph.addCourse(bus201);
        graph.addCourse(econ201);
        graph.addCourse(bus301);
        graph.addCourse(bus401);
    }
}
