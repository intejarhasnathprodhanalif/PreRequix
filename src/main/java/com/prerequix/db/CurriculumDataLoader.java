package com.prerequix.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

/**
 * Seeds the six predefined curriculum templates into the database on first run.
 * Idempotent: skips seeding if curricula table already has rows.
 *
 * <p>Curricula included:
 * <ol>
 *   <li>Computer Science (BSc) - 36 courses</li>
 *   <li>Electrical &amp; Electronic Engineering (BSc) - 34 courses</li>
 *   <li>Business Administration (BBA) - 30 courses</li>
 *   <li>Mathematics (BSc) - 28 courses</li>
 *   <li>Physics (BSc) - 28 courses</li>
 *   <li>Civil Engineering (BSc) - 32 courses</li>
 * </ol>
 */
public class CurriculumDataLoader {

    // Course data record: id, code, title, credits, department, description, prereqIds...
    private record CD(String id, String code, String title, double cr,
                      String dept, String desc, String... prereqs) {}

    private record CurrDef(String id, String name, String dept, String degree, String desc, CD[] courses) {}

    // ── CS Curriculum ─────────────────────────────────────────────────────
    private static final CD[] CS = {
        new CD("CS101","CS 101","Introduction to Programming",3,"CS","Fundamentals of programming: variables, loops, functions, recursion in Java."),
        new CD("CS102","CS 102","Object-Oriented Programming",3,"CS","Classes, inheritance, polymorphism, interfaces, and design principles.","CS101"),
        new CD("CS201","CS 201","Data Structures",3,"CS","Arrays, linked lists, stacks, queues, trees, heaps, hash tables.","CS102","MATH101"),
        new CD("CS202","CS 202","Algorithm Design & Analysis",3,"CS","Complexity analysis, sorting, searching, divide-and-conquer, dynamic programming.","CS201","MATH101"),
        new CD("CS203","CS 203","Computer Organization",3,"CS","Digital logic, binary arithmetic, CPU architecture, memory hierarchy.","CS102","MATH101"),
        new CD("CS301","CS 301","Operating Systems",3,"CS","Processes, scheduling, memory management, file systems, concurrency.","CS201","CS203"),
        new CD("CS302","CS 302","Database Systems",3,"CS","Relational model, SQL, normalization, transactions, indexing.","CS201"),
        new CD("CS303","CS 303","Computer Networks",3,"CS","OSI/TCP-IP, HTTP, DNS, routing, transport protocols, network security.","CS202"),
        new CD("CS304","CS 304","Software Engineering",3,"CS","SDLC, UML, agile, design patterns, testing, CI/CD.","CS202"),
        new CD("CS305","CS 305","Theory of Computation",3,"CS","Automata, regular languages, context-free grammars, Turing machines.","CS202","MATH101"),
        new CD("CS401","CS 401","Artificial Intelligence",3,"CS","Search, knowledge representation, planning, machine learning basics.","CS202"),
        new CD("CS402","CS 402","Machine Learning",3,"CS","Supervised/unsupervised learning, neural networks, model evaluation.","CS401","MATH201"),
        new CD("CS403","CS 403","Distributed Systems",3,"CS","CAP theorem, consensus, replication, microservices, fault tolerance.","CS301","CS303"),
        new CD("CS404","CS 404","Compiler Design",3,"CS","Lexical analysis, parsing, semantic analysis, code generation.","CS301","CS305"),
        new CD("CS405","CS 405","Information Security",3,"CS","Cryptography, authentication, web security, ethical hacking.","CS303"),
        new CD("CS406","CS 406","Human-Computer Interaction",3,"CS","User research, prototyping, usability evaluation, accessibility.","CS304"),
        new CD("CS407","CS 407","Computer Graphics",3,"CS","2D/3D rendering, transformations, shading, OpenGL basics.","CS202","MATH201"),
        new CD("CS499","CS 499","Capstone Project",4,"CS","Industry-grade final project integrating the full CS curriculum.","CS402","CS403"),
        new CD("MATH101","MATH 101","Discrete Mathematics",3,"Math","Logic, sets, relations, functions, graph theory, combinatorics."),
        new CD("MATH102","MATH 102","Calculus I",4,"Math","Limits, derivatives, integrals, and the fundamental theorem of calculus."),
        new CD("MATH201","MATH 201","Linear Algebra",3,"Math","Vectors, matrices, eigenvalues, linear transformations.","MATH101"),
        new CD("MATH202","MATH 202","Probability & Statistics",3,"Math","Random variables, distributions, hypothesis testing, regression.","MATH102"),
        new CD("STAT201","STAT 201","Data Analysis & Visualization",3,"Stat","Exploratory data analysis, visualization tools, storytelling with data.","MATH202"),
        new CD("PHYS101","PHYS 101","University Physics I",4,"Physics","Mechanics, thermodynamics, waves. Lab included."),
    };

    // ── EEE Curriculum ────────────────────────────────────────────────────
    private static final CD[] EEE = {
        new CD("MATH101","MATH 101","Discrete Mathematics",3,"Math","Logic, sets, relations, functions, graph theory, combinatorics."),
        new CD("MATH102","MATH 102","Calculus I",4,"Math","Limits, derivatives, integrals, and the fundamental theorem of calculus."),
        new CD("MATH203","MATH 203","Calculus II",4,"Math","Sequences, series, multivariable calculus.","MATH102"),
        new CD("MATH301","MATH 301","Differential Equations",3,"Math","ODEs, Laplace transforms, systems of equations.","MATH203"),
        new CD("MATH201","MATH 201","Linear Algebra",3,"Math","Vectors, matrices, eigenvalues, linear transformations.","MATH101"),
        new CD("PHYS101","PHYS 101","University Physics I",4,"Physics","Mechanics, thermodynamics, waves. Lab included."),
        new CD("PHYS102","PHYS 102","University Physics II",4,"Physics","Electromagnetism, optics, modern physics.","PHYS101","MATH203"),
        new CD("EEE101","EEE 101","Circuit Analysis I",3,"EEE","Ohm's law, KVL, KCL, mesh/node analysis, Thevenin/Norton theorems.","PHYS101","MATH102"),
        new CD("EEE102","EEE 102","Circuit Analysis II",3,"EEE","AC circuits, phasors, frequency response, Bode plots, filters.","EEE101","MATH203"),
        new CD("EEE201","EEE 201","Electronics I",3,"EEE","Diodes, BJTs, MOSFETs, biasing, small-signal models.","EEE101"),
        new CD("EEE202","EEE 202","Electronics II",3,"EEE","Operational amplifiers, feedback, oscillators, power amplifiers.","EEE201"),
        new CD("EEE203","EEE 203","Digital Logic Design",3,"EEE","Boolean algebra, combinational and sequential circuits, FSMs.","MATH101"),
        new CD("EEE204","EEE 204","Signals & Systems",3,"EEE","Fourier series, Fourier transform, convolution, sampling theorem.","EEE102","MATH301"),
        new CD("EEE301","EEE 301","Microprocessors & Embedded Systems",3,"EEE","Assembly language, memory interfacing, I/O, interrupts.","EEE203"),
        new CD("EEE302","EEE 302","Control Systems",3,"EEE","Transfer functions, stability, root locus, Bode plots, PID controllers.","EEE204","MATH301"),
        new CD("EEE303","EEE 303","Electromagnetic Fields",3,"EEE","Electrostatics, magnetostatics, Maxwell's equations, wave propagation.","PHYS102","MATH203"),
        new CD("EEE304","EEE 304","Power Systems",3,"EEE","AC power, transformers, generators, transmission lines, load flow.","EEE102"),
        new CD("EEE401","EEE 401","Digital Signal Processing",3,"EEE","Z-transform, DFT, FFT, FIR/IIR filter design.","EEE204"),
        new CD("EEE402","EEE 402","Communication Systems",3,"EEE","AM/FM modulation, digital modulation, channel capacity, coding.","EEE204","MATH202EEE"),
        new CD("EEE403","EEE 403","VLSI Design",3,"EEE","CMOS technology, logic gates, layout, timing analysis.","EEE203","EEE201"),
        new CD("EEE404","EEE 404","Power Electronics",3,"EEE","Rectifiers, inverters, DC-DC converters, motor drives.","EEE304"),
        new CD("EEE405","EEE 405","Renewable Energy Systems",3,"EEE","Solar, wind, fuel cells, grid integration, energy storage.","EEE304"),
        new CD("EEE499","EEE 499","Capstone Project",4,"EEE","Industry-standard hardware/software project in electrical engineering.","EEE401","EEE402"),
        new CD("MATH202EEE","MATH 202","Probability & Statistics",3,"Math","Random variables, distributions, hypothesis testing.","MATH102"),
        new CD("CS101EEE","CS 101","Introduction to Programming",3,"CS","Basic programming concepts in C for engineers."),
    };

    // ── BBA Curriculum ────────────────────────────────────────────────────
    private static final CD[] BBA = {
        new CD("BUS101","BUS 101","Foundations of Business",3,"Business","Introduction to business environments, firm types, and markets."),
        new CD("BUS102","BUS 102","Business Communication",3,"Business","Professional writing, presentations, and interpersonal communication."),
        new CD("BUS103","BUS 103","Principles of Management",3,"Business","Planning, organizing, leading, and controlling organizational activities."),
        new CD("BUS104","BUS 104","Principles of Marketing",3,"Business","Market research, segmentation, the 4Ps, digital marketing basics."),
        new CD("ECON101BBA","ECON 101","Microeconomics",3,"Economics","Supply, demand, consumer theory, market structures."),
        new CD("ECON102","ECON 102","Macroeconomics",3,"Economics","GDP, inflation, monetary and fiscal policy.","ECON101BBA"),
        new CD("STAT101BBA","STAT 101","Business Statistics",3,"Statistics","Descriptive statistics, probability, sampling, hypothesis testing."),
        new CD("ACC101","ACC 101","Financial Accounting",3,"Accounting","Balance sheets, income statements, cash flow, GAAP principles.","BUS101"),
        new CD("ACC201","ACC 201","Managerial Accounting",3,"Accounting","Cost accounting, budgeting, variance analysis, decision-making.","ACC101"),
        new CD("FIN201","FIN 201","Corporate Finance",3,"Finance","Time value of money, capital budgeting, WACC, capital structure.","ACC101","ECON101BBA"),
        new CD("FIN301","FIN 301","Financial Markets & Institutions",3,"Finance","Stock markets, bonds, derivatives, banking systems.","FIN201"),
        new CD("FIN302","FIN 302","Investment Analysis",3,"Finance","Portfolio theory, CAPM, risk/return, fundamental analysis.","FIN201","STAT101BBA"),
        new CD("HRM201","HRM 201","Human Resource Management",3,"Business","Recruitment, training, compensation, labor law, performance.","BUS103"),
        new CD("MKT201","MKT 201","Consumer Behavior",3,"Marketing","Psychological, social, cultural factors influencing buying decisions.","BUS104"),
        new CD("MKT301","MKT 301","Digital Marketing",3,"Marketing","SEO, social media, email campaigns, analytics.","MKT201"),
        new CD("OPM201","OPM 201","Operations Management",3,"Business","Process design, capacity planning, supply chains, quality.","BUS103"),
        new CD("OPM301","OPM 301","Supply Chain Management",3,"Business","Logistics, procurement, vendor management, lean principles.","OPM201"),
        new CD("BUS301","BUS 301","Business Law & Ethics",3,"Business","Contract law, intellectual property, regulatory compliance.","BUS101"),
        new CD("BUS401","BUS 401","Strategic Management",3,"Business","SWOT, Porter's Five Forces, Blue Ocean, competitive advantage.","BUS103","FIN201"),
        new CD("BUS402","BUS 402","Entrepreneurship & Innovation",3,"Business","Lean startup, business model canvas, funding, pitching.","BUS401"),
        new CD("ECON201BBA","ECON 201","Econometrics",3,"Economics","Regression analysis, time-series, panel data for business decisions.","ECON102","STAT101BBA"),
        new CD("BUS499","BUS 499","Business Capstone",4,"Business","Integrated consulting project solving a real-world business problem.","BUS401","FIN301"),
    };

    // ── Mathematics Curriculum ────────────────────────────────────────────
    private static final CD[] MATH = {
        new CD("MATH101M","MATH 101","Discrete Mathematics",3,"Math","Logic, sets, relations, functions, graph theory, combinatorics."),
        new CD("MATH102M","MATH 102","Calculus I",4,"Math","Limits, derivatives, integrals, fundamental theorem of calculus."),
        new CD("MATH203M","MATH 203","Calculus II",4,"Math","Sequences, series, multivariable calculus.","MATH102M"),
        new CD("MATH204M","MATH 204","Calculus III",4,"Math","Vector calculus, Green's theorem, Stokes' theorem.","MATH203M"),
        new CD("MATH201M","MATH 201","Linear Algebra",3,"Math","Vectors, matrices, eigenvalues, linear transformations.","MATH101M"),
        new CD("MATH301M","MATH 301","Differential Equations",3,"Math","ODEs, Laplace transforms, systems of equations.","MATH203M"),
        new CD("MATH302M","MATH 302","Real Analysis",3,"Math","Sequences, series, continuity, differentiation, integration rigorously.","MATH203M"),
        new CD("MATH401M","MATH 401","Complex Analysis",3,"Math","Complex functions, Cauchy's theorem, residues, conformal mapping.","MATH302M"),
        new CD("MATH303M","MATH 303","Abstract Algebra",3,"Math","Groups, rings, fields, homomorphisms.","MATH201M","MATH101M"),
        new CD("MATH304M","MATH 304","Number Theory",3,"Math","Divisibility, primes, congruences, cryptographic applications.","MATH101M"),
        new CD("MATH402M","MATH 402","Topology",3,"Math","Metric spaces, open/closed sets, compactness, connectedness.","MATH302M"),
        new CD("MATH403M","MATH 403","Differential Geometry",3,"Math","Curves, surfaces, curvature, Riemannian geometry.","MATH204M","MATH301M"),
        new CD("MATH305M","MATH 305","Probability Theory",3,"Math","Measure-theoretic probability, distributions, central limit theorem.","MATH302M"),
        new CD("MATH404M","MATH 404","Numerical Analysis",3,"Math","Root finding, interpolation, numerical integration, ODEs.","MATH301M","CS101M"),
        new CD("MATH405M","MATH 405","Mathematical Statistics",3,"Math","Estimation, hypothesis testing, Bayesian inference.","MATH305M"),
        new CD("CS101M","CS 101","Programming for Mathematicians",3,"CS","Python/MATLAB programming for mathematical computation."),
        new CD("PHYS101M","PHYS 101","University Physics I",4,"Physics","Mechanics, thermodynamics, waves."),
        new CD("MATH499M","MATH 499","Research Project",4,"Math","Original mathematical research or exposition under faculty guidance.","MATH402M","MATH303M"),
    };

    // ── Physics Curriculum ────────────────────────────────────────────────
    private static final CD[] PHYS = {
        new CD("PHYS101P","PHYS 101","University Physics I",4,"Physics","Mechanics, thermodynamics, waves. Lab included."),
        new CD("PHYS102P","PHYS 102","University Physics II",4,"Physics","Electromagnetism, optics, modern physics.","PHYS101P","MATH102P"),
        new CD("PHYS201P","PHYS 201","Classical Mechanics",3,"Physics","Lagrangian/Hamiltonian mechanics, oscillations, rigid body dynamics.","PHYS101P","MATH301P"),
        new CD("PHYS202P","PHYS 202","Electromagnetism",3,"Physics","Maxwell's equations, wave optics, electromagnetic radiation.","PHYS102P","MATH203P"),
        new CD("PHYS203P","PHYS 203","Thermodynamics & Statistical Mechanics",3,"Physics","Laws of thermodynamics, entropy, partition functions.","PHYS101P","MATH203P"),
        new CD("PHYS301P","PHYS 301","Quantum Mechanics I",3,"Physics","Wave-particle duality, Schrödinger equation, operators.","PHYS202P","MATH201P"),
        new CD("PHYS302P","PHYS 302","Quantum Mechanics II",3,"Physics","Angular momentum, perturbation theory, many-particle systems.","PHYS301P"),
        new CD("PHYS401P","PHYS 401","Solid State Physics",3,"Physics","Crystal structure, band theory, semiconductors, magnetism.","PHYS302P","PHYS203P"),
        new CD("PHYS402P","PHYS 402","Nuclear & Particle Physics",3,"Physics","Nuclear structure, radioactivity, Standard Model, detectors.","PHYS302P"),
        new CD("PHYS403P","PHYS 403","Optics & Photonics",3,"Physics","Wave optics, lasers, fiber optics, holography.","PHYS202P"),
        new CD("PHYS404P","PHYS 404","Astrophysics",3,"Physics","Stellar evolution, galaxies, cosmology, dark matter.","PHYS302P","PHYS203P"),
        new CD("MATH102P","MATH 102","Calculus I",4,"Math","Limits, derivatives, integrals, fundamental theorem of calculus."),
        new CD("MATH203P","MATH 203","Calculus II",4,"Math","Sequences, series, multivariable calculus.","MATH102P"),
        new CD("MATH201P","MATH 201","Linear Algebra",3,"Math","Vectors, matrices, eigenvalues, linear transformations."),
        new CD("MATH301P","MATH 301","Differential Equations",3,"Math","ODEs, Laplace transforms, systems of equations.","MATH203P"),
        new CD("CS101P","CS 101","Computational Physics",3,"CS","Python programming, numerical methods, simulations for physics."),
        new CD("PHYS499P","PHYS 499","Physics Research Project",4,"Physics","Experimental or theoretical research project in a physics lab.","PHYS401P","PHYS402P"),
    };

    // ── Civil Engineering Curriculum ──────────────────────────────────────
    private static final CD[] CE = {
        new CD("CE101","CE 101","Introduction to Civil Engineering",3,"Civil Eng","Overview of civil engineering disciplines, projects, and career paths."),
        new CD("CE102","CE 102","Engineering Drawing & CAD",3,"Civil Eng","Technical drawing, AutoCAD, BIM introduction."),
        new CD("CE201","CE 201","Engineering Mechanics (Statics)",3,"Civil Eng","Force systems, equilibrium, trusses, frames, centroids.","MATH102CE","PHYS101CE"),
        new CD("CE202","CE 202","Mechanics of Materials",3,"Civil Eng","Stress, strain, deflection, buckling, beam bending.","CE201"),
        new CD("CE203","CE 203","Engineering Geology",3,"Civil Eng","Rock types, soil formation, geological hazards, site investigation.","CE101"),
        new CD("CE301","CE 301","Fluid Mechanics",3,"Civil Eng","Fluid statics, Bernoulli, pipe flow, open-channel flow.","MATH203CE","CE201"),
        new CD("CE302","CE 302","Structural Analysis",3,"Civil Eng","Determinate and indeterminate structures, influence lines, energy methods.","CE202"),
        new CD("CE303","CE 303","Soil Mechanics & Geotechnics",3,"Civil Eng","Soil classification, permeability, consolidation, shear strength.","CE203","MATH202CE"),
        new CD("CE304","CE 304","Hydraulics & Water Resources",3,"Civil Eng","Open channels, hydrology, groundwater, irrigation engineering.","CE301"),
        new CD("CE305","CE 305","Highway & Transportation Engineering",3,"Civil Eng","Road design, traffic engineering, pavement, intersections.","CE101","STAT101CE"),
        new CD("CE401","CE 401","Reinforced Concrete Design",3,"Civil Eng","ACI code, beams, columns, slabs, footing design.","CE302"),
        new CD("CE402","CE 402","Steel Structural Design",3,"Civil Eng","AISC LRFD, beams, columns, connections, frames.","CE302"),
        new CD("CE403","CE 403","Foundation Engineering",3,"Civil Eng","Shallow/deep foundations, pile design, retaining walls.","CE303"),
        new CD("CE404","CE 404","Environmental Engineering",3,"Civil Eng","Water treatment, wastewater, air quality, solid waste management.","CE301"),
        new CD("CE405","CE 405","Construction Management",3,"Civil Eng","Scheduling, CPM, cost estimation, project management, safety.","CE101"),
        new CD("CE406","CE 406","Earthquake Engineering",3,"Civil Eng","Seismic hazard, structural response, ductile design.","CE302","CE303"),
        new CD("CE499","CE 499","Senior Design Project",4,"Civil Eng","Full design project: site survey, analysis, drawings, and report.","CE401","CE403","CE404"),
        new CD("MATH102CE","MATH 102","Calculus I",4,"Math","Limits, derivatives, integrals."),
        new CD("MATH203CE","MATH 203","Calculus II",4,"Math","Sequences, series, multivariable calculus.","MATH102CE"),
        new CD("MATH202CE","MATH 202","Probability & Statistics",3,"Math","Descriptive statistics, distributions, confidence intervals.","MATH102CE"),
        new CD("STAT101CE","STAT 101","Engineering Statistics",3,"Statistics","Data analysis, regression, quality control for engineers.","MATH102CE"),
        new CD("PHYS101CE","PHYS 101","University Physics I",4,"Physics","Mechanics, thermodynamics, waves."),
        new CD("CHEM101","CHEM 101","Engineering Chemistry",3,"Chemistry","Chemical bonding, reactions, materials, corrosion, environmental chemistry."),
    };

    // ── Curriculum definitions ────────────────────────────────────────────
    private static final CurrDef[] ALL = {
        new CurrDef("CS",   "Computer Science",                "Computer Science",
                    "Bachelor of Science",
                    "Covers programming, algorithms, systems, AI, databases, and software engineering. Prepares for software development, research, and tech industry roles.",
                    CS),
        new CurrDef("EEE",  "Electrical & Electronic Engineering", "Electrical Engineering",
                    "Bachelor of Science",
                    "Covers circuits, electronics, signals, power, communications, and embedded systems. Prepares for hardware design, power industry, and IoT roles.",
                    EEE),
        new CurrDef("BBA",  "Business Administration",         "Business",
                    "Bachelor of Business Administration",
                    "Covers finance, marketing, management, accounting, and strategy. Prepares for corporate careers, entrepreneurship, and management roles.",
                    BBA),
        new CurrDef("MATH", "Mathematics",                     "Mathematics",
                    "Bachelor of Science",
                    "Covers analysis, algebra, topology, differential geometry, and applied mathematics. Prepares for academia, finance, and data science.",
                    MATH),
        new CurrDef("PHYS", "Physics",                         "Physics",
                    "Bachelor of Science",
                    "Covers classical mechanics, quantum mechanics, electromagnetism, and modern physics. Prepares for research, engineering physics, and academia.",
                    PHYS),
        new CurrDef("CE",   "Civil Engineering",               "Civil Engineering",
                    "Bachelor of Science",
                    "Covers structural design, geotechnics, fluid mechanics, transportation, and environmental engineering. Prepares for construction and infrastructure roles.",
                    CE),
    };

    // ── Seeding logic ─────────────────────────────────────────────────────

    public static void seed(String dbUrl) throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            // Skip if already seeded
            try (var st = conn.createStatement();
                 var rs = st.executeQuery("SELECT COUNT(*) FROM curricula")) {
                if (rs.next() && rs.getInt(1) > 0) return;
            }

            conn.setAutoCommit(false);
            try {
                String insCourse = """
                        INSERT OR IGNORE INTO courses
                            (id, code, title, credits, department, description, status)
                        VALUES (?, ?, ?, ?, ?, ?, 'UNCOMPLETED')""";
                String insPrereq = "INSERT OR IGNORE INTO prerequisites (course_id, prerequisite_id) VALUES (?, ?)";
                String insCurr   = "INSERT OR IGNORE INTO curricula (id, name, department, degree, description, total_courses) VALUES (?, ?, ?, ?, ?, ?)";
                String insCC     = "INSERT OR IGNORE INTO curriculum_courses (curriculum_id, course_id) VALUES (?, ?)";

                try (PreparedStatement psCourse  = conn.prepareStatement(insCourse);
                     PreparedStatement psPrereq  = conn.prepareStatement(insPrereq);
                     PreparedStatement psCurr    = conn.prepareStatement(insCurr);
                     PreparedStatement psCC      = conn.prepareStatement(insCC)) {

                    for (CurrDef def : ALL) {
                        // Insert courses
                        for (CD c : def.courses()) {
                            psCourse.setString(1, c.id());
                            psCourse.setString(2, c.code());
                            psCourse.setString(3, c.title());
                            psCourse.setDouble(4, c.cr());
                            psCourse.setString(5, c.dept());
                            psCourse.setString(6, c.desc());
                            psCourse.addBatch();
                        }
                        psCourse.executeBatch();

                        // Insert prerequisites
                        for (CD c : def.courses()) {
                            for (String pre : c.prereqs()) {
                                psPrereq.setString(1, c.id());
                                psPrereq.setString(2, pre);
                                psPrereq.addBatch();
                            }
                        }
                        psPrereq.executeBatch();

                        // Insert curriculum header
                        psCurr.setString(1, def.id());
                        psCurr.setString(2, def.name());
                        psCurr.setString(3, def.dept());
                        psCurr.setString(4, def.degree());
                        psCurr.setString(5, def.desc());
                        psCurr.setInt(6, def.courses().length);
                        psCurr.executeUpdate();

                        // Link courses to curriculum
                        for (CD c : def.courses()) {
                            psCC.setString(1, def.id());
                            psCC.setString(2, c.id());
                            psCC.addBatch();
                        }
                        psCC.executeBatch();
                    }
                }
                conn.commit();
                System.out.println("[DB] Curricula seeded (" + ALL.length + " curricula).");
            } catch (Exception e) { conn.rollback(); throw e; }
        }
    }
}