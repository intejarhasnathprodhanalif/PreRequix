package com.prerequix.concurrent;

import com.prerequix.model.CourseGraph;
import com.prerequix.storage.CourseStorageManager;
import javafx.concurrent.Task;

/**
 * Background {@link Task} that loads the graph from JSON (or falls back to
 * the CS preset) on the I/O thread, keeping the UI responsive at startup.
 *
 * Returns {@code true} if a saved file was loaded, {@code false} if the
 * default preset was used instead.
 */
public class LoadDataTask extends Task<Boolean> {

    private final CourseGraph graph;
    private final CourseStorageManager storageManager;

    public LoadDataTask(CourseGraph graph, CourseStorageManager storageManager) {
        this.graph = graph;
        this.storageManager = storageManager;
    }

    @Override
    protected Boolean call() {
        updateMessage("Loading course data…");
        boolean loaded = storageManager.loadGraph(graph);
        if (!loaded) {
            updateMessage("No saved data found – loading CS preset…");
            storageManager.loadComputerSciencePreset(graph);
        } else {
            updateMessage("Courses loaded successfully.");
        }
        return loaded;
    }
}
