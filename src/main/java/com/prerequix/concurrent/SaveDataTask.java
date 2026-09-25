package com.prerequix.concurrent;

import com.prerequix.model.CourseGraph;
import com.prerequix.storage.CourseStorageManager;
import javafx.concurrent.Task;

/**
 * Background {@link Task} that saves the graph to JSON on the I/O executor
 * thread so the UI never blocks during a file-write.
 *
 * <p>Returns {@code true} on success, {@code false} on failure.
 * Check {@link #getException()} in the {@code onFailed} handler for details.</p>
 */
public class SaveDataTask extends Task<Boolean> {

    private final CourseGraph graph;
    private final CourseStorageManager storageManager;

    public SaveDataTask(CourseGraph graph, CourseStorageManager storageManager) {
        this.graph = graph;
        this.storageManager = storageManager;
    }

    @Override
    protected Boolean call() throws Exception {
        updateMessage("Saving courses to disk…");
        storageManager.saveGraph(graph);
        updateMessage("Saved successfully.");
        return Boolean.TRUE;
    }
}
