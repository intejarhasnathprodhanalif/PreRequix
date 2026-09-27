package com.prerequix;

/**
 * Bootstrap launcher for IDEs (IntelliJ IDEA, Eclipse, VS Code).
 *
 * <p>When running JavaFX applications from standard IDE run buttons (without modular VM options),
 * the Java launcher checks if the main class extends {@link javafx.application.Application}.
 * If it does, it expects JavaFX to be in the module path and errors.
 * Having this separate bootstrap class bypasses that check and starts JavaFX smoothly.
 */
public class Launcher {

    public static void main(String[] args) {
        App.main(args);
    }
}
